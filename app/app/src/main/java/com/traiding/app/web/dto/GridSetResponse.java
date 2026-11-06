package com.traiding.app.web.dto;

import com.traiding.app.domain.GridSet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vista de un conjunto de niveles. */
public record GridSetResponse(
        Long id,
        String symbol,
        BigDecimal floorPrice,
        BigDecimal ceilPrice,
        Integer levelCount,
        BigDecimal btcInventory,
        BigDecimal usdtReserve,
        BigDecimal feeRate,
        String status,
        int cycleSells,
        LocalDateTime createdAt,
        LocalDateTime exhaustedAt,
        Long originSetId
) {
    public static GridSetResponse from(GridSet set) {
        return new GridSetResponse(
                set.getId(),
                set.getSymbol(),
                set.getFloorPrice(),
                set.getCeilPrice(),
                set.getLevelCount(),
                set.getBtcInventory(),
                set.getUsdtReserve(),
                set.getFeeRate(),
                set.getStatus(),
                set.getCycleSells(),
                set.getCreatedAt(),
                set.getExhaustedAt(),
                set.getOriginSetId());
    }
}
