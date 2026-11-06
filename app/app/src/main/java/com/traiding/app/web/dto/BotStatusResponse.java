package com.traiding.app.web.dto;

import java.math.BigDecimal;

/** Estado del bot. */
public record BotStatusResponse(
        boolean running,
        String feed,
        String symbol,
        BigDecimal lastPrice,
        long activeSets,
        long pendingAlerts
) {
}
