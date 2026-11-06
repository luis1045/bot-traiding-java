package com.traiding.app.service;

import com.traiding.app.domain.GridAlert;
import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;
import com.traiding.app.domain.GridSetStatus;
import com.traiding.app.domain.Trade;
import com.traiding.app.repository.GridAlertRepository;
import com.traiding.app.repository.GridLevelRepository;
import com.traiding.app.repository.GridSetRepository;
import com.traiding.app.web.dto.ApproveBuyRequest;
import com.traiding.app.web.dto.ApproveSellRequest;
import com.traiding.app.web.dto.GridAlertResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Modo manual: el motor ya no compra ni vende. Cuando el precio entra en la zona de un nivel se
 * genera una alerta ({@code PENDING}); el usuario decide y registra la operación con sus datos
 * reales (cantidad, precio y, en las ventas, el profit).
 *
 * <p>Se crea como máximo una alerta pendiente por nivel. Si el usuario descarta una alerta, no
 * se vuelve a avisar hasta que el precio abandone esa zona (para no repetir el aviso en cada
 * tick).</p>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DISMISSED = "DISMISSED";
    public static final String BUY = "BUY";
    public static final String SELL = "SELL";

    private final GridAlertRepository alertRepository;
    private final GridLevelRepository levelRepository;
    private final GridSetRepository setRepository;
    private final GridExecutor executor;
    private final PriceHolder priceHolder;

    /** Zona descartada por el usuario en cada nivel (nivel → zona), hasta que el precio salga. */
    private final Map<Long, String> mutedZones = new ConcurrentHashMap<>();

    public AlertService(GridAlertRepository alertRepository,
                        GridLevelRepository levelRepository,
                        GridSetRepository setRepository,
                        GridExecutor executor,
                        PriceHolder priceHolder) {
        this.alertRepository = alertRepository;
        this.levelRepository = levelRepository;
        this.setRepository = setRepository;
        this.executor = executor;
        this.priceHolder = priceHolder;
    }

    // ------------------------------------------------------------------ detección de zonas

    /** Evalúa todos los niveles de un conjunto activo y crea las alertas que falten. */
    public Mono<Void> evaluate(GridSet set, List<GridLevel> levels, BigDecimal price) {
        return alertRepository.findByGridSetIdAndStatusOrderByIdAsc(set.getId(), PENDING)
                .map(GridAlert::getGridLevelId)
                .collect(Collectors.toSet())
                .flatMap(pendingLevels -> Flux.fromIterable(levels)
                        .concatMap(level -> evaluateLevel(set, level, price, pendingLevels))
                        .then());
    }

    private Mono<Void> evaluateLevel(GridSet set, GridLevel level, BigDecimal price,
                                     Set<Long> pendingLevels) {
        String zone = zoneOf(level, price);
        if (zone == null) {
            mutedZones.remove(level.getId());
            return Mono.empty();
        }
        if (zone.equals(mutedZones.get(level.getId()))) {
            return Mono.empty();
        }
        if (pendingLevels.contains(level.getId())) {
            return Mono.empty();
        }
        GridAlert alert = new GridAlert();
        alert.setGridSetId(set.getId());
        alert.setGridLevelId(level.getId());
        alert.setLevelIndex(level.getLevelIndex());
        alert.setType(zone);
        alert.setStatus(PENDING);
        alert.setTriggerPrice(price);
        alert.setSuggestedQuantity(level.getBtcAllocated());
        alert.setSuggestedPrice(price);
        alert.setCreatedAt(nowUtc());
        return alertRepository.save(alert)
                .doOnSuccess(saved -> log.info("ALERTA {} conjunto {} nivel {} @ {} ({} BTC)",
                        zone, set.getId(), level.getLevelIndex(), price, level.getBtcAllocated()))
                .then();
    }

    private String zoneOf(GridLevel level, BigDecimal price) {
        if (level.isHolding() && price.compareTo(level.getUpperPrice()) >= 0) {
            return SELL;
        }
        if (!level.isHolding() && price.compareTo(level.getLowerPrice()) <= 0) {
            return BUY;
        }
        return null;
    }

    // ------------------------------------------------------------------ consultas

    public Flux<GridAlertResponse> pending() {
        return alertRepository.findByStatusOrderByIdAsc(PENDING)
                .concatMap(this::toResponse);
    }

    public Flux<GridAlertResponse> bySet(Long setId) {
        return alertRepository.findByGridSetIdOrderByIdAsc(setId)
                .concatMap(this::toResponse);
    }

    private Mono<GridAlertResponse> toResponse(GridAlert alert) {
        return Mono.zip(
                        setRepository.findById(alert.getGridSetId()),
                        levelRepository.findById(alert.getGridLevelId()))
                .map(tuple -> GridAlertResponse.from(
                        alert, tuple.getT1(), tuple.getT2(), priceHolder.get()));
    }

    // ------------------------------------------------------------------ operaciones manuales

    /** Registra la compra indicada por el usuario y cierra la alerta. */
    public Mono<Trade> approveBuy(Long alertId, ApproveBuyRequest request) {
        return pendingAlert(alertId, BUY)
                .flatMap(alert -> activeSet(alert)
                        .flatMap(set -> levelRepository.findById(alert.getGridLevelId())
                                .switchIfEmpty(Mono.error(new NotFoundException(
                                        "Nivel " + alert.getGridLevelId() + " no encontrado")))
                                .flatMap(level -> {
                                    if (level.isHolding()) {
                                        return Mono.error(new BadRequestException(
                                                "El nivel " + level.getLevelIndex()
                                                        + " ya tiene BTC: no procede registrar otra compra"));
                                    }
                                    boolean overdraft = Boolean.TRUE.equals(request.allowOverdraft());
                                    BigDecimal total = money(request.price()
                                            .multiply(request.quantityBtc())
                                            .multiply(BigDecimal.ONE.add(set.getFeeRate())));
                                    if (!overdraft && set.getUsdtReserve().compareTo(total) < 0) {
                                        return Mono.error(new BadRequestException(
                                                "La reserva declarada (" + set.getUsdtReserve()
                                                        + " USDT) no alcanza para esta compra (" + total
                                                        + " USDT). Reduce la cantidad o confirma el registro"
                                                        + " aunque la reserva quede negativa."));
                                    }
                                    // La cantidad del nivel pasa a ser la realmente comprada, para que el
                                    // siguiente aviso de venta sugiera el importe correcto.
                                    level.setBtcAllocated(money(request.quantityBtc()));
                                    return executor.buy(set, level, request.price(),
                                                    request.quantityBtc(), true)
                                            .flatMap(trade -> resolve(alert, APPROVED, trade.getId(),
                                                    request.note())
                                                    .thenReturn(trade));
                                })));
    }

    /** Registra la venta indicada por el usuario (cantidad, precio y profit) y cierra la alerta. */
    public Mono<Trade> approveSell(Long alertId, ApproveSellRequest request) {
        return pendingAlert(alertId, SELL)
                .flatMap(alert -> activeSet(alert)
                        .flatMap(set -> levelRepository.findById(alert.getGridLevelId())
                                .switchIfEmpty(Mono.error(new NotFoundException(
                                        "Nivel " + alert.getGridLevelId() + " no encontrado")))
                                .flatMap(level -> {
                                    if (!level.isHolding()) {
                                        return Mono.error(new BadRequestException(
                                                "El nivel " + level.getLevelIndex()
                                                        + " no tiene BTC: no procede registrar la venta"));
                                    }
                                    return executor.sell(set, level, request.price(),
                                                    request.quantityBtc(), request.pnlUsdt(),
                                                    request.pnlBtc())
                                            .flatMap(trade -> resolve(alert, APPROVED, trade.getId(),
                                                            request.note())
                                                    .then(evaluateExhaustion(set, request.price()))
                                                    .thenReturn(trade));
                                })));
    }

    /** Descarta la alerta sin registrar operación; no se repite hasta que el precio salga de la zona. */
    public Mono<GridAlertResponse> dismiss(Long alertId, String note) {
        return pendingAlert(alertId, null)
                .flatMap(alert -> {
                    mutedZones.put(alert.getGridLevelId(), alert.getType());
                    return resolve(alert, DISMISSED, null, note).then(toResponse(alert));
                });
    }

    private Mono<GridAlert> pendingAlert(Long alertId, String expectedType) {
        return alertRepository.findById(alertId)
                .switchIfEmpty(Mono.error(new NotFoundException("Alerta " + alertId + " no encontrada")))
                .flatMap(alert -> {
                    if (!PENDING.equals(alert.getStatus())) {
                        return Mono.error(new BadRequestException(
                                "La alerta " + alertId + " ya está " + alert.getStatus()));
                    }
                    if (expectedType != null && !expectedType.equals(alert.getType())) {
                        return Mono.error(new BadRequestException("La alerta " + alertId
                                + " es de tipo " + alert.getType() + ", no " + expectedType));
                    }
                    return Mono.just(alert);
                });
    }

    private Mono<GridSet> activeSet(GridAlert alert) {
        return setRepository.findById(alert.getGridSetId())
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "Conjunto " + alert.getGridSetId() + " no encontrado")))
                .flatMap(set -> GridSetStatus.ACTIVE.name().equals(set.getStatus())
                        ? Mono.just(set)
                        : Mono.error(new BadRequestException("El conjunto " + set.getId()
                                + " no está activo (estado: " + set.getStatus() + ")")));
    }

    private Mono<Void> resolve(GridAlert alert, String status, Long tradeId, String note) {
        alert.setStatus(status);
        alert.setResolvedAt(nowUtc());
        alert.setTradeId(tradeId);
        if (note != null && !note.isBlank()) {
            alert.setNote(note.trim());
        }
        return alertRepository.save(alert).then();
    }

    // ------------------------------------------------------------------ agotamiento

    /**
     * Tras una venta manual: si todos los niveles han completado su venta en el ciclo actual, ya no
     * queda BTC y el precio está sobre el techo, el conjunto pasa a {@code EXHAUSTED} y se descartan
     * las alertas que quedaran pendientes.
     */
    private Mono<Void> evaluateExhaustion(GridSet set, BigDecimal price) {
        BigDecimal reference = price != null ? price : priceHolder.get();
        return levelRepository.findByGridSetIdOrderByLevelIndexAsc(set.getId())
                .collectList()
                .flatMap(levels -> {
                    boolean everyLevelSold = !levels.isEmpty()
                            && levels.stream().allMatch(level -> !level.isHolding()
                                    && level.getCycleSells() > 0);
                    boolean aboveCeiling = reference != null
                            && reference.compareTo(set.getCeilPrice()) >= 0;

                    if (!everyLevelSold || !aboveCeiling) {
                        return Mono.empty();
                    }
                    log.info("Conjunto {} AGOTADO: precio {} superó el techo {}",
                            set.getId(), reference, set.getCeilPrice());
                    set.setStatus(GridSetStatus.EXHAUSTED.name());
                    set.setExhaustedAt(nowUtc());
                    return setRepository.save(set)
                            .then(dismissPendingOfSet(set.getId()));
                });
    }

    public Mono<Void> dismissPendingOfSet(Long setId) {
        return alertRepository.findByGridSetIdAndStatusOrderByIdAsc(setId, PENDING)
                .concatMap(alert -> {
                    alert.setStatus(DISMISSED);
                    alert.setResolvedAt(nowUtc());
                    alert.setNote("Conjunto agotado");
                    return alertRepository.save(alert);
                })
                .then();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(GridMath.SCALE, RoundingMode.HALF_UP);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
