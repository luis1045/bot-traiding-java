package com.traiding.app.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reporte de ganancias de un conjunto, desglosado por nivel. Distingue la ganancia
 * <b>realizada</b> (de las ventas ya cerradas) de la <b>no realizada</b> (valoración a precio
 * actual de lo que sigue en cartera) y su <b>total</b>, tanto en USDT como en BTC.
 */
public record GridReportResponse(
        Long gridSetId,
        String symbol,
        String status,
        BigDecimal floorPrice,
        BigDecimal ceilPrice,
        int levelCount,
        BigDecimal btcInventory,
        BigDecimal btcHeld,
        BigDecimal usdtReserve,
        BigDecimal feeRate,
        BigDecimal currentPrice,
        LocalDateTime generatedAt,
        int totalTrades,
        int levelsTraded,
        BigDecimal realizedPnlUsdt,
        BigDecimal realizedPnlBtc,
        BigDecimal unrealizedPnlUsdt,
        BigDecimal unrealizedPnlBtc,
        BigDecimal totalPnlUsdt,
        BigDecimal totalPnlBtc,
        List<LevelReportResponse> levels
) {
}
