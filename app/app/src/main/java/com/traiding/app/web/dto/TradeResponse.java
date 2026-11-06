package com.traiding.app.web.dto;

import com.traiding.app.domain.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vista de una operación. */
public record TradeResponse(
        Long id,
        Long gridSetId,
        Integer levelIndex,
        String side,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal pnlUsdt,
        BigDecimal pnlBtc,
        LocalDateTime createdAt
) {
    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getGridSetId(),
                trade.getLevelIndex(),
                trade.getSide(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getAmount(),
                trade.getFee(),
                trade.getPnlUsdt(),
                trade.getPnlBtc(),
                trade.getCreatedAt());
    }
}
