package com.traiding.app.service;

import com.traiding.app.config.BotProperties;
import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;
import com.traiding.app.domain.GridSetStatus;
import com.traiding.app.repository.GridAlertRepository;
import com.traiding.app.repository.GridLevelRepository;
import com.traiding.app.repository.GridSetRepository;
import com.traiding.app.repository.TradeRepository;
import com.traiding.app.web.dto.CreateGridRequest;
import com.traiding.app.web.dto.PnlResponse;
import com.traiding.app.web.dto.ResolveRequest;
import com.traiding.app.domain.Trade;
import com.traiding.app.domain.TradeSide;
import com.traiding.app.web.dto.GridReportResponse;
import com.traiding.app.web.dto.LevelReportResponse;
import com.traiding.app.web.error.BadRequestException;
import com.traiding.app.web.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de grillas: creación, consulta y resolución del estado agotado.
 */
@Service
public class GridService {

    private static final Logger log = LoggerFactory.getLogger(GridService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal PERCENT_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final GridSetRepository setRepository;
    private final GridLevelRepository levelRepository;
    private final TradeRepository tradeRepository;
    private final GridAlertRepository alertRepository;
    private final PriceHolder priceHolder;
    private final BotProperties properties;

    public GridService(GridSetRepository setRepository,
                       GridLevelRepository levelRepository,
                       TradeRepository tradeRepository,
                       GridAlertRepository alertRepository,
                       PriceHolder priceHolder,
                       BotProperties properties) {
        this.setRepository = setRepository;
        this.levelRepository = levelRepository;
        this.tradeRepository = tradeRepository;
        this.alertRepository = alertRepository;
        this.priceHolder = priceHolder;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ creación

    public Mono<GridSet> createSet(CreateGridRequest request) {
        return Mono.defer(() -> {
            List<BigDecimal> percents = resolvePercents(request.levelCount(), request.levelPercents());
            validateRange(request.floorPrice(), request.ceilPrice(), request.levelCount(), request.btcInventory());

            BigDecimal feeRate = request.feeRate() != null ? request.feeRate() : properties.getFeeRate();
            BigDecimal reserve = request.usdtReserve() != null ? request.usdtReserve() : BigDecimal.ZERO;
            boolean startInBtc = request.startInBtc() == null || request.startInBtc();
            BigDecimal entryPrice = currentPriceOrMidpoint(request.floorPrice(), request.ceilPrice());

            GridSet set = new GridSet();
            set.setSymbol(properties.getSymbol());
            set.setFloorPrice(request.floorPrice());
            set.setCeilPrice(request.ceilPrice());
            set.setLevelCount(request.levelCount());
            set.setBtcInventory(request.btcInventory());
            set.setUsdtReserve(reserve);
            set.setFeeRate(feeRate);
            set.setStatus(GridSetStatus.ACTIVE.name());
            set.setCycleSells(0);
            set.setCreatedAt(nowUtc());

            return persistNewSet(set, percents, startInBtc, entryPrice);
        });
    }

    private Mono<GridSet> persistNewSet(GridSet set, List<BigDecimal> percents,
                                        boolean startInBtc, BigDecimal entryPrice) {
        return setRepository.save(set)
                .flatMap(saved -> {
                    List<GridLevel> levels = buildLevels(
                            saved.getId(),
                            GridMath.boundaries(saved.getFloorPrice(), saved.getCeilPrice(), saved.getLevelCount()),
                            percents,
                            saved.getBtcInventory(),
                            startInBtc,
                            entryPrice);
                    log.info("Creado conjunto {} con {} niveles entre {} y {} (BTC repartido por porcentaje)",
                            saved.getId(), saved.getLevelCount(), saved.getFloorPrice(), saved.getCeilPrice());
                    return levelRepository.saveAll(levels).then().thenReturn(saved);
                });
    }

    // ------------------------------------------------------------------ resolución del estado agotado

    public Mono<GridSet> resolve(Long setId, ResolveRequest request) {
        return setRepository.findById(setId)
                .switchIfEmpty(Mono.error(new NotFoundException("Conjunto " + setId + " no encontrado")))
                .flatMap(set -> {
                    GridSetStatus status = set.statusEnum();
                    if (status != GridSetStatus.EXHAUSTED) {
                        return Mono.error(new BadRequestException(
                                "El conjunto no está agotado (estado actual: " + status + ")"));
                    }
                    String action = request.action() == null ? "" : request.action().trim().toUpperCase();
                    return switch (action) {
                        case "CONTINUE" -> continueSet(set);
                        case "NEW_SET" -> newSet(set, request);
                        default -> Mono.error(new BadRequestException(
                                "Acción inválida: '" + request.action() + "'. Use CONTINUE o NEW_SET"));
                    };
                });
    }

    /** Reanuda el mismo conjunto: los niveles ya están vendidos y volverán a comprar al bajar. */
    private Mono<GridSet> continueSet(GridSet set) {
        set.setStatus(GridSetStatus.ACTIVE.name());
        set.setExhaustedAt(null);
        set.setCycleSells(0);
        return setRepository.save(set)
                .thenMany(levelRepository.findByGridSetIdOrderByLevelIndexAsc(set.getId()))
                .concatMap(level -> {
                    level.setCycleSells(0);
                    return levelRepository.save(level);
                })
                .then(setRepository.findById(set.getId()))
                .doOnSuccess(saved -> log.info("Conjunto {} reanudado con los mismos niveles", saved.getId()));
    }

    /** Cierra el conjunto actual y crea uno nuevo con otro piso/techo, heredando el efectivo. */
    private Mono<GridSet> newSet(GridSet oldSet, ResolveRequest request) {
        if (request.floorPrice() == null || request.ceilPrice() == null) {
            return Mono.error(new BadRequestException(
                    "Para crear un nuevo conjunto debe indicar floorPrice y ceilPrice"));
        }
        int levelCount = request.levelCount() != null ? request.levelCount() : oldSet.getLevelCount();
        List<BigDecimal> percents = resolvePercents(levelCount, request.levelPercents());
        BigDecimal inventory = request.btcInventory() != null ? request.btcInventory() : oldSet.getBtcInventory();
        validateRange(request.floorPrice(), request.ceilPrice(), levelCount, inventory);

        BigDecimal transferred = oldSet.getUsdtReserve();
        oldSet.setStatus(GridSetStatus.CLOSED.name());
        oldSet.setUsdtReserve(BigDecimal.ZERO);

        GridSet fresh = new GridSet();
        fresh.setSymbol(oldSet.getSymbol());
        fresh.setFloorPrice(request.floorPrice());
        fresh.setCeilPrice(request.ceilPrice());
        fresh.setLevelCount(levelCount);
        fresh.setBtcInventory(inventory);
        fresh.setUsdtReserve(transferred);
        fresh.setFeeRate(oldSet.getFeeRate());
        fresh.setStatus(GridSetStatus.ACTIVE.name());
        fresh.setCycleSells(0);
        fresh.setCreatedAt(nowUtc());
        fresh.setOriginSetId(oldSet.getId());

        return setRepository.save(oldSet)
                .then(setRepository.save(fresh))
                .flatMap(saved -> persistNewSet(saved, percents, false, null))
                .doOnSuccess(saved -> log.info("Nuevo conjunto {} creado desde {} con {} USDT transferidos",
                        saved.getId(), oldSet.getId(), transferred));
    }

    // ------------------------------------------------------------------ consultas

    public Flux<GridSet> listSets() {
        return setRepository.findAllByOrderByCreatedAtDesc();
    }

    public Mono<GridSet> getSet(Long id) {
        return setRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Conjunto " + id + " no encontrado")));
    }

    public Flux<GridLevel> levels(Long setId) {
        return levelRepository.findByGridSetIdOrderByLevelIndexAsc(setId);
    }

    public Flux<Trade> trades(Long setId) {
        return tradeRepository.findByGridSetIdOrderByCreatedAtDesc(setId);
    }

    // ------------------------------------------------------------------ borrado

    /** Elimina un conjunto junto con sus niveles, sus alertas y su historial de operaciones. */
    public Mono<Void> deleteSet(Long setId) {
        return getSet(setId)
                .then(alertRepository.deleteByGridSetId(setId))
                .then(tradeRepository.deleteByGridSetId(setId))
                .then(levelRepository.deleteByGridSetId(setId))
                .then(setRepository.deleteById(setId))
                .doOnSuccess(v -> log.info("Conjunto {} eliminado (niveles, alertas y operaciones)", setId));
    }

    public Mono<PnlResponse> pnl(Long setId) {
        return getSet(setId)
                .flatMap(set -> levelRepository.findByGridSetIdOrderByLevelIndexAsc(setId)
                        .collectList()
                        .map(levels -> {
                            BigDecimal pnlUsdt = BigDecimal.ZERO;
                            BigDecimal pnlBtc = BigDecimal.ZERO;
                            BigDecimal btcHeld = BigDecimal.ZERO;
                            int totalTrades = 0;
                            int levelsTraded = 0;

                            for (GridLevel level : levels) {
                                pnlUsdt = pnlUsdt.add(level.getPnlUsdt());
                                pnlBtc = pnlBtc.add(level.getPnlBtc());
                                totalTrades += level.getTradeCount();
                                if (level.getTradeCount() > 0) {
                                    levelsTraded++;
                                }
                                if (level.isHolding()) {
                                    btcHeld = btcHeld.add(level.getBtcAllocated());
                                }
                            }

                            return new PnlResponse(
                                    set.getId(),
                                    pnlUsdt.setScale(GridMath.SCALE, RoundingMode.HALF_UP),
                                    pnlBtc.setScale(GridMath.SCALE, RoundingMode.HALF_UP),
                                    btcHeld.setScale(GridMath.SCALE, RoundingMode.HALF_UP),
                                    set.getBtcInventory(),
                                    set.getUsdtReserve(),
                                    totalTrades,
                                    levelsTraded,
                                    levels.stream().map(com.traiding.app.web.dto.GridLevelResponse::from).toList());
                        }));
    }

    // ------------------------------------------------------------------ reporte de ganancias

    /**
     * Reporte de ganancias por nivel. La ganancia <b>realizada</b> sale de las ventas cerradas
     * (ya acumulada en cada nivel) y la <b>no realizada</b> valora a precio actual lo que sigue
     * en cartera, descontando comisiones de entrada y de salida.
     */
    public Mono<GridReportResponse> report(Long setId) {
        return getSet(setId).flatMap(set ->
                Mono.zip(
                                levelRepository.findByGridSetIdOrderByLevelIndexAsc(setId).collectList(),
                                tradeRepository.findByGridSetIdOrderByCreatedAtDesc(setId).collectList())
                        .map(tuple -> buildReport(set, tuple.getT1(), tuple.getT2())));
    }

    private GridReportResponse buildReport(GridSet set, List<GridLevel> levels, List<Trade> trades) {
        BigDecimal currentPrice = priceHolder.get();
        BigDecimal buyCostFactor = BigDecimal.ONE.add(set.getFeeRate());
        BigDecimal sellNetFactor = BigDecimal.ONE.subtract(set.getFeeRate());

        Map<Integer, int[]> counts = new HashMap<>();
        for (Trade trade : trades) {
            int[] pair = counts.computeIfAbsent(trade.getLevelIndex(), key -> new int[2]);
            if (TradeSide.BUY.name().equals(trade.getSide())) {
                pair[0]++;
            } else {
                pair[1]++;
            }
        }

        List<LevelReportResponse> rows = new ArrayList<>(levels.size());
        BigDecimal realizedUsdt = BigDecimal.ZERO;
        BigDecimal realizedBtc = BigDecimal.ZERO;
        BigDecimal unrealizedUsdt = BigDecimal.ZERO;
        BigDecimal unrealizedBtc = BigDecimal.ZERO;
        BigDecimal btcHeld = BigDecimal.ZERO;
        int totalTrades = 0;
        int levelsTraded = 0;

        for (GridLevel level : levels) {
            int[] pair = counts.getOrDefault(level.getLevelIndex(), new int[2]);
            BigDecimal levelRealizedUsdt = money(level.getPnlUsdt());
            BigDecimal levelRealizedBtc = money(level.getPnlBtc());

            BigDecimal levelUnrealizedUsdt = BigDecimal.ZERO;
            BigDecimal levelUnrealizedBtc = BigDecimal.ZERO;
            if (level.isHolding() && level.getEntryPrice() != null && currentPrice != null) {
                BigDecimal quantity = level.getBtcAllocated();
                BigDecimal netProceeds = money(currentPrice.multiply(quantity).multiply(sellNetFactor));
                BigDecimal costBase = money(level.getEntryPrice().multiply(quantity).multiply(buyCostFactor));
                levelUnrealizedUsdt = money(netProceeds.subtract(costBase));
                levelUnrealizedBtc = currentPrice.signum() == 0
                        ? BigDecimal.ZERO
                        : levelUnrealizedUsdt.divide(currentPrice, GridMath.SCALE, RoundingMode.HALF_UP);
                btcHeld = btcHeld.add(quantity);
            }

            realizedUsdt = realizedUsdt.add(levelRealizedUsdt);
            realizedBtc = realizedBtc.add(levelRealizedBtc);
            unrealizedUsdt = unrealizedUsdt.add(levelUnrealizedUsdt);
            unrealizedBtc = unrealizedBtc.add(levelUnrealizedBtc);
            totalTrades += level.getTradeCount();
            if (level.getTradeCount() > 0) {
                levelsTraded++;
            }

            rows.add(new LevelReportResponse(
                    level.getLevelIndex(),
                    level.getLowerPrice(),
                    level.getUpperPrice(),
                    level.getWeightPercent(),
                    level.getBtcAllocated(),
                    level.isHolding(),
                    level.getEntryPrice(),
                    pair[0],
                    pair[1],
                    money(level.getBtcBought()),
                    money(level.getUsdtSpent()),
                    money(level.getBtcSold()),
                    money(level.getUsdtReceived()),
                    levelRealizedUsdt,
                    levelRealizedBtc,
                    levelUnrealizedUsdt,
                    levelUnrealizedBtc,
                    money(levelRealizedUsdt.add(levelUnrealizedUsdt)),
                    money(levelRealizedBtc.add(levelUnrealizedBtc))));
        }

        return new GridReportResponse(
                set.getId(),
                set.getSymbol(),
                set.getStatus(),
                set.getFloorPrice(),
                set.getCeilPrice(),
                set.getLevelCount(),
                set.getBtcInventory(),
                money(btcHeld),
                set.getUsdtReserve(),
                set.getFeeRate(),
                currentPrice,
                nowUtc(),
                totalTrades,
                levelsTraded,
                money(realizedUsdt),
                money(realizedBtc),
                money(unrealizedUsdt),
                money(unrealizedBtc),
                money(realizedUsdt.add(unrealizedUsdt)),
                money(realizedBtc.add(unrealizedBtc)),
                rows);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(GridMath.SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ utilidades

    private List<GridLevel> buildLevels(Long setId, List<BigDecimal> boundaries, List<BigDecimal> percents,
                                        BigDecimal inventory, boolean holding, BigDecimal entryPrice) {
        List<GridLevel> levels = new ArrayList<>(percents.size());
        for (int i = 0; i < percents.size(); i++) {
            GridLevel level = new GridLevel();
            level.setGridSetId(setId);
            level.setLevelIndex(i + 1);
            level.setLowerPrice(boundaries.get(i));
            level.setUpperPrice(boundaries.get(i + 1));
            level.setWeightPercent(percents.get(i));
            level.setBtcAllocated(GridMath.pct(inventory, percents.get(i)));
            level.setHolding(holding);
            level.setEntryPrice(holding ? entryPrice : null);
            levels.add(level);
        }
        return levels;
    }

    /**
     * Normaliza y valida el reparto de porcentajes: si el usuario no los indica se reparten a
     * partes iguales; si los indica deben ser uno por nivel y sumar 100%.
     */
    private List<BigDecimal> resolvePercents(int levelCount, List<BigDecimal> provided) {
        if (provided == null || provided.isEmpty()) {
            return GridMath.equalPercents(levelCount);
        }
        if (provided.size() != levelCount) {
            throw new BadRequestException(
                    "Debe indicar un porcentaje por nivel: se esperaban " + levelCount + " y llegaron " + provided.size());
        }
        for (BigDecimal percent : provided) {
            if (percent == null || percent.signum() <= 0) {
                throw new BadRequestException("Cada porcentaje debe ser mayor que cero");
            }
        }
        BigDecimal sum = GridMath.sum(provided);
        if (sum.subtract(HUNDRED).abs().compareTo(PERCENT_TOLERANCE) > 0) {
            throw new BadRequestException("Los porcentajes deben sumar 100%. Suma actual: " + sum);
        }
        return provided;
    }

    private void validateRange(BigDecimal floor, BigDecimal ceil, int levelCount, BigDecimal inventory) {
        if (floor == null || ceil == null) {
            throw new BadRequestException("Debe indicar floorPrice y ceilPrice");
        }
        if (floor.compareTo(ceil) >= 0) {
            throw new BadRequestException("El piso (" + floor + ") debe ser menor que el techo (" + ceil + ")");
        }
        if (levelCount < 1) {
            throw new BadRequestException("El número de niveles debe ser al menos 1");
        }
        if (inventory == null || inventory.signum() <= 0) {
            throw new BadRequestException("El inventario de BTC debe ser mayor que cero");
        }
    }

    private BigDecimal currentPriceOrMidpoint(BigDecimal floor, BigDecimal ceil) {
        BigDecimal midpoint = floor.add(ceil).divide(TWO, GridMath.SCALE, RoundingMode.HALF_UP);
        return priceHolder.getOrElse(midpoint).setScale(GridMath.SCALE, RoundingMode.HALF_UP);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
