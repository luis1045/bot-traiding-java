package com.traiding.app.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Decisión del usuario cuando el conjunto quedó {@code EXHAUSTED}:
 * {@code CONTINUE} (seguir con los niveles actuales) o {@code NEW_SET} (crear uno nuevo).
 */
public record ResolveRequest(
        @NotBlank String action,
        @DecimalMin(value = "0", inclusive = false) BigDecimal floorPrice,
        @DecimalMin(value = "0", inclusive = false) BigDecimal ceilPrice,
        Integer levelCount,
        BigDecimal btcInventory,
        List<@DecimalMin(value = "0", inclusive = false) BigDecimal> levelPercents
) {
}
