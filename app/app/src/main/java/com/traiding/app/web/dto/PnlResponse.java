package com.traiding.app.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ganancias consolidadas de un conjunto, en BTC y en USDT. */
public record PnlResponse(
        Long gridSetId,
        BigDecimal pnlUsdt,
        BigDecimal pnlBtc,
        BigDecimal btcHeld,
        BigDecimal btcInventory,
        BigDecimal usdtReserve,
        int totalTrades,
        int levelsTraded,
        List<GridLevelResponse> levels
) {
}
