package com.traiding.app.service.price;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta de la API pública de Binance para {@code GET /api/v3/ticker/price}.
 * El precio llega como texto para no perder precisión.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTicker(String symbol, String price) {
}
