package com.traiding.app.service;

import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;
import com.traiding.app.domain.Trade;
import com.traiding.app.domain.TradeSide;
import com.traiding.app.repository.GridLevelRepository;
import com.traiding.app.repository.GridSetRepository;
import com.traiding.app.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Ejecuta compras y ventas simuladas (paper trading) actualizando el nivel y el efectivo del
 * conjunto, y dejando registro en {@code trade_history}.
 */
@Component
public class GridExecutor {

    private static final Logger log = LoggerFactory.getLogger(GridExecutor.class);

    private final TradeRepository tradeRepository;
    private final GridLevelRepository levelRepository;
    private final GridSetRepository setRepository;

    public GridExecutor(TradeRepository tradeRepository,
                        GridLevelRepository levelRepository,
                        GridSetRepository setRepository) {
        this.tradeRepository = tradeRepository;
        this.levelRepository = levelRepository;
        this.setRepository = setRepository;
    }

    /**
     * Compra la cantidad asignada del nivel al precio indicado.
     * Si no hay efectivo suficiente, no hace nada (devuelve {@code Mono.empty()}).
     */
    public Mono<Trade> buy(GridSet set, GridLevel level, BigDecimal price) {
        BigDecimal quantity = level.getBtcAllocated();
        return buy(set, level, price, quantity);
    }

    public Mono<Trade> buy(GridSet set, GridLevel level, BigDecimal price, BigDecimal quantity) {
        return buy(set, level, price, quantity, false);
    }

    /**
     * Compra una cantidad concreta al precio indicado.
     *
     * <p>Con {@code allowOverdraft = true} se registra la operación aunque la reserva declarada
     * no alcance (modo manual: se anota lo que el usuario ha operado realmente y la reserva
     * puede quedar negativa). Con {@code false} se mantiene la comprobación de efectivo.</p>
     */
    public Mono<Trade> buy(GridSet set, GridLevel level, BigDecimal price, BigDecimal quantity,
                           boolean allowOverdraft) {
        BigDecimal amount = money(price.multiply(quantity));
        BigDecimal fee = money(amount.multiply(set.getFeeRate()));
        BigDecimal total = money(amount.add(fee));

        if (!allowOverdraft && set.getUsdtReserve().compareTo(total) < 0) {
            log.debug("Nivel {} sin efectivo suficiente: necesita {} y hay {}",
                    level.getLevelIndex(), total, set.getUsdtReserve());
            return Mono.empty();
        }

        set.setUsdtReserve(money(set.getUsdtReserve().subtract(total)));
        level.setHolding(true);
        level.setEntryPrice(price);
        level.setBtcBought(money(level.getBtcBought().add(quantity)));
        level.setUsdtSpent(money(level.getUsdtSpent().add(total)));
        level.setTradeCount(level.getTradeCount() + 1);

        Trade trade = newTrade(set, level, TradeSide.BUY, price, quantity, amount, fee,
                BigDecimal.ZERO, BigDecimal.ZERO);
        return persist(set, level, trade)
                .doOnSuccess(t -> log.info("COMPRA nivel {} @ {} ({} BTC, comision {})",
                        level.getLevelIndex(), price, quantity, fee));
    }

    /**
     * Vende la cantidad indicada al precio dado, realizando la ganancia en USDT y en BTC
     * respecto al precio de entrada del nivel.
     */
    public Mono<Trade> sell(GridSet set, GridLevel level, BigDecimal price) {
        BigDecimal quantity = level.getBtcAllocated();
        return sell(set, level, price, quantity);
    }

    public Mono<Trade> sell(GridSet set, GridLevel level, BigDecimal price, BigDecimal quantity) {
        return sell(set, level, price, quantity, null, null);
    }

    /**
     * Vende una cantidad concreta al precio indicado.
     *
     * <p>El PnL se calcula con el precio de entrada y la comisión del conjunto, pero en modo
     * manual el usuario puede aportar su propio {@code pnlUsdt} y/o {@code pnlBtc}. Si solo
     * aporta el PnL en USDT, el PnL en BTC se deriva de este.</p>
     */
    public Mono<Trade> sell(GridSet set, GridLevel level, BigDecimal price, BigDecimal quantity,
                            BigDecimal pnlUsdtOverride, BigDecimal pnlBtcOverride) {
        BigDecimal amount = money(price.multiply(quantity));
        BigDecimal fee = money(amount.multiply(set.getFeeRate()));
        BigDecimal proceeds = money(amount.subtract(fee));

        BigDecimal entry = level.getEntryPrice() != null ? level.getEntryPrice() : price;
        // Coste de adquisición incluyendo la comisión de compra proporcional.
        BigDecimal costBase = money(entry.multiply(quantity).multiply(BigDecimal.ONE.add(set.getFeeRate())));
        BigDecimal pnlUsdt = pnlUsdtOverride != null
                ? money(pnlUsdtOverride)
                : money(amount.subtract(fee).subtract(costBase));
        BigDecimal pnlBtc = pnlBtcOverride != null
                ? money(pnlBtcOverride)
                : price.signum() == 0
                        ? BigDecimal.ZERO
                        : pnlUsdt.divide(price, GridMath.SCALE, RoundingMode.HALF_UP);

        set.setUsdtReserve(money(set.getUsdtReserve().add(proceeds)));
        set.setCycleSells(set.getCycleSells() + 1);
        level.setHolding(false);
        level.setEntryPrice(null);
        level.setBtcSold(money(level.getBtcSold().add(quantity)));
        level.setUsdtReceived(money(level.getUsdtReceived().add(proceeds)));
        level.setPnlUsdt(money(level.getPnlUsdt().add(pnlUsdt)));
        level.setPnlBtc(money(level.getPnlBtc().add(pnlBtc)));
        level.setTradeCount(level.getTradeCount() + 1);
        level.setCycleSells(level.getCycleSells() + 1);

        // Reinversión por nivel: la ganancia realizada se suma a la cantidad del nivel. La próxima
        // compra (en el borde inferior) se financia con lo recaudado en esta venta, así el nivel
        // opera cada ciclo con más BTC y el conjunto no pierde efectivo (compounding por nivel).
        if (level.getLowerPrice() != null && level.getLowerPrice().signum() > 0) {
            BigDecimal divisor = level.getLowerPrice().multiply(BigDecimal.ONE.add(set.getFeeRate()));
            BigDecimal nextQty = proceeds.divide(divisor, GridMath.SCALE, RoundingMode.HALF_UP);
            level.setBtcAllocated(money(nextQty));
        }

        Trade trade = newTrade(set, level, TradeSide.SELL, price, quantity, amount, fee,
                pnlUsdt, pnlBtc);
        return persist(set, level, trade)
                .doOnSuccess(t -> log.info(
                        "VENTA nivel {} @ {} ({} BTC, pnl {} USDT / {} BTC, cantidad pasa a {} BTC)",
                        level.getLevelIndex(), price, quantity, pnlUsdt, pnlBtc, level.getBtcAllocated()));
    }

    private Mono<Trade> persist(GridSet set, GridLevel level, Trade trade) {
        return levelRepository.save(level)
                .then(setRepository.save(set))
                .then(tradeRepository.save(trade));
    }

    private Trade newTrade(GridSet set, GridLevel level, TradeSide side, BigDecimal price,
                           BigDecimal quantity, BigDecimal amount, BigDecimal fee,
                           BigDecimal pnlUsdt, BigDecimal pnlBtc) {
        Trade trade = new Trade();
        trade.setGridSetId(set.getId());
        trade.setGridLevelId(level.getId());
        trade.setLevelIndex(level.getLevelIndex());
        trade.setSide(side.name());
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setAmount(amount);
        trade.setFee(fee);
        trade.setPnlUsdt(pnlUsdt);
        trade.setPnlBtc(pnlBtc);
        trade.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return trade;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(GridMath.SCALE, RoundingMode.HALF_UP);
    }
}
