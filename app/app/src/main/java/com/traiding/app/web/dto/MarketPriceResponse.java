package com.traiding.app.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Precio público de mercado de un par (fuente: Binance, sin API keys). */
public record MarketPriceResponse(
        String symbol,
        BigDecimal price,
        String source,
        Instant time
) {
}
