package com.traiding.app.service.price;

import reactor.core.publisher.Flux;

import java.math.BigDecimal;

/**
 * Fuente de precios del par. Emite el último precio negociado (spot) cada vez que cambia
 * o cada cierto intervalo.
 */
public interface PriceFeed {

    /** Identificador de la fuente, por ejemplo {@code simulated} o {@code binance}. */
    String name();

    /** Flujo infinito de precios del par. */
    Flux<BigDecimal> prices();
}
