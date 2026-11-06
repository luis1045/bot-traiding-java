package com.traiding.app.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Registro manual de una venta a partir de una alerta de zona de venta.
 *
 * @param quantityBtc BTC realmente vendidos
 * @param price       precio de venta
 * @param pnlUsdt     profit en USDT aportado por el usuario; si se omite se calcula con el
 *                    precio de entrada y las comisiones del conjunto
 * @param pnlBtc      profit en BTC; si se omite se deriva del profit en USDT
 * @param note        nota/estado libre (opcional)
 */
public record ApproveSellRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityBtc,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price,
        BigDecimal pnlUsdt,
        BigDecimal pnlBtc,
        @Size(max = 255) String note
) {
}
