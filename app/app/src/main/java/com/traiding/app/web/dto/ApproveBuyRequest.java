package com.traiding.app.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Registro manual de una compra a partir de una alerta de zona de compra.
 *
 * @param quantityBtc    BTC realmente comprados
 * @param price          precio de compra
 * @param note           nota/estado libre (opcional)
 * @param allowOverdraft si la reserva declarada no alcanza, registrar igualmente (opcional)
 */
public record ApproveBuyRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityBtc,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price,
        @Size(max = 255) String note,
        Boolean allowOverdraft
) {
}
