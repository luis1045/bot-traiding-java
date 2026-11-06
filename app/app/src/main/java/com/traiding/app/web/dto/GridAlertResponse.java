package com.traiding.app.web.dto;

import com.traiding.app.domain.GridAlert;
import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de una alerta de zona de compra/venta, enriquecida con los datos del conjunto y del
 * nivel para que el panel pueda pedir la operación manual sin consultas adicionales.
 */
public record GridAlertResponse(
        Long id,
        Long gridSetId,
        Long gridLevelId,
        Integer levelIndex,
        String type,
        String status,
        BigDecimal triggerPrice,
        BigDecimal suggestedQuantity,
        BigDecimal suggestedPrice,
        String symbol,
        BigDecimal floorPrice,
        BigDecimal ceilPrice,
        String setStatus,
        BigDecimal feeRate,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        boolean holding,
        BigDecimal entryPrice,
        BigDecimal currentPrice,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        Long tradeId,
        String note
) {
    public static GridAlertResponse from(GridAlert alert, GridSet set, GridLevel level,
                                         BigDecimal currentPrice) {
        return new GridAlertResponse(
                alert.getId(),
                alert.getGridSetId(),
                alert.getGridLevelId(),
                alert.getLevelIndex(),
                alert.getType(),
                alert.getStatus(),
                alert.getTriggerPrice(),
                alert.getSuggestedQuantity(),
                alert.getSuggestedPrice(),
                set.getSymbol(),
                set.getFloorPrice(),
                set.getCeilPrice(),
                set.getStatus(),
                set.getFeeRate(),
                level.getLowerPrice(),
                level.getUpperPrice(),
                level.isHolding(),
                level.getEntryPrice(),
                currentPrice,
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getTradeId(),
                alert.getNote());
    }
}
