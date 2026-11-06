package com.traiding.app.web.dto;

import com.traiding.app.domain.GridLevel;

import java.math.BigDecimal;

/** Vista de un nivel de la grilla. */
public record GridLevelResponse(
        Long id,
        Integer levelIndex,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        BigDecimal weightPercent,
        BigDecimal btcAllocated,
        boolean holding,
        BigDecimal entryPrice,
        BigDecimal btcBought,
        BigDecimal usdtSpent,
        BigDecimal btcSold,
        BigDecimal usdtReceived,
        BigDecimal pnlUsdt,
        BigDecimal pnlBtc,
        int tradeCount
) {
    public static GridLevelResponse from(GridLevel level) {
        return new GridLevelResponse(
                level.getId(),
                level.getLevelIndex(),
                level.getLowerPrice(),
                level.getUpperPrice(),
                level.getWeightPercent(),
                level.getBtcAllocated(),
                level.isHolding(),
                level.getEntryPrice(),
                level.getBtcBought(),
                level.getUsdtSpent(),
                level.getBtcSold(),
                level.getUsdtReceived(),
                level.getPnlUsdt(),
                level.getPnlBtc(),
                level.getTradeCount());
    }
}
