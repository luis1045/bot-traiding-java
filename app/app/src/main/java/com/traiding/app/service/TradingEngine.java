package com.traiding.app.service;

import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;
import com.traiding.app.domain.GridSetStatus;
import com.traiding.app.repository.GridLevelRepository;
import com.traiding.app.repository.GridSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Motor de la grilla en <b>modo manual</b>. Por cada precio recibido evalúa todos los conjuntos
 * activos y avisa al usuario de las zonas alcanzadas; no ejecuta ninguna operación:
 *
 * <ul>
 *   <li>Nivel con BTC ({@code holding}) y precio &ge; borde superior &rarr; alerta de VENTA.</li>
 *   <li>Nivel en efectivo y precio &le; borde inferior &rarr; alerta de COMPRA.</li>
 * </ul>
 *
 * <p>Las operaciones las registra el usuario desde el panel ({@link AlertService}). Cuando todos
 * los niveles se han vendido y el precio supera el techo, el conjunto pasa a {@code EXHAUSTED}:
 * todo el BTC se convirtió a USDT y se habilita la decisión del usuario (continuar o crear un
 * nuevo conjunto).</p>
 */
@Component
public class TradingEngine {

    private static final Logger log = LoggerFactory.getLogger(TradingEngine.class);

    private final GridSetRepository setRepository;
    private final GridLevelRepository levelRepository;
    private final AlertService alertService;

    public TradingEngine(GridSetRepository setRepository,
                         GridLevelRepository levelRepository,
                         AlertService alertService) {
        this.setRepository = setRepository;
        this.levelRepository = levelRepository;
        this.alertService = alertService;
    }

    /** Procesa un precio nuevo. Secuencial para evitar condiciones de carrera. */
    public Mono<Void> onPrice(BigDecimal price) {
        return setRepository.findByStatus(GridSetStatus.ACTIVE.name())
                .concatMap(set -> processSet(set, price))
                .then();
    }

    private Mono<Void> processSet(GridSet set, BigDecimal price) {
        return levelRepository.findByGridSetIdOrderByLevelIndexAsc(set.getId())
                .collectList()
                .flatMap(levels -> alertService.evaluate(set, levels, price))
                .then(Mono.defer(() -> evaluateExhaustion(set, price)));
    }

    private Mono<Void> evaluateExhaustion(GridSet set, BigDecimal price) {
        return levelRepository.findByGridSetIdOrderByLevelIndexAsc(set.getId())
                .collectList()
                .flatMap(levels -> {
                    boolean everyLevelSold = !levels.isEmpty()
                            && levels.stream().allMatch(level -> !level.isHolding()
                                    && level.getCycleSells() > 0);
                    boolean aboveCeiling = price.compareTo(set.getCeilPrice()) >= 0;

                    if (everyLevelSold && aboveCeiling) {
                        log.info("Conjunto {} AGOTADO: precio {} superó el techo {}", set.getId(), price, set.getCeilPrice());
                        set.setStatus(GridSetStatus.EXHAUSTED.name());
                        set.setExhaustedAt(LocalDateTime.now(ZoneOffset.UTC));
                        return setRepository.save(set)
                                .then(alertService.dismissPendingOfSet(set.getId()));
                    }
                    return Mono.empty();
                });
    }
}
