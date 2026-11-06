package com.traiding.app.web.dto;

import java.math.BigDecimal;

/**
 * Fila del reporte de ganancias de un nivel: operaciones de compra/venta, ganancia
 * <b>realizada</b> (ventas ya cerradas), ganancia <b>no realizada</b> (mark-to-market de lo que
 * sigue en cartera) y ganancia <b>total</b>.
 */
public record LevelReportResponse(
        int levelIndex,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        BigDecimal weightPercent,
        BigDecimal btcAllocated,
        boolean holding,
        BigDecimal entryPrice,
        int buys,
        int sells,
        BigDecimal btcBought,
        BigDecimal usdtSpent,
        BigDecimal btcSold,
        BigDecimal usdtReceived,
        BigDecimal realizedPnlUsdt,
        BigDecimal realizedPnlBtc,
        BigDecimal unrealizedPnlUsdt,
        BigDecimal unrealizedPnlBtc,
        BigDecimal totalPnlUsdt,
        BigDecimal totalPnlBtc
) {
}
