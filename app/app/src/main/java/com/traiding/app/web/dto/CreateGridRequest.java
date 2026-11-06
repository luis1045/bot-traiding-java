package com.traiding.app.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Petición para crear un conjunto de niveles.
 *
 * @param floorPrice   piso de la grilla
 * @param ceilPrice    techo de la grilla
 * @param levelCount   número de niveles (n)
 * @param btcInventory inventario total de BTC a repartir entre los niveles
 * @param usdtReserve  efectivo en USDT disponible para las compras (opcional, por defecto 0)
 * @param levelPercents porcentaje de BTC por nivel; si se omite se reparte equitativamente (opcional)
 * @param feeRate      comisión por operación; si se omite se usa la configurada (opcional)
 * @param startInBtc   true si el bot ya posee el BTC (empieza sosteniendo); false si empieza en efectivo
 */
public record CreateGridRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal floorPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal ceilPrice,
        @NotNull @Min(1) @Max(1000) Integer levelCount,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal btcInventory,
        @DecimalMin(value = "0") BigDecimal usdtReserve,
        List<@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal> levelPercents,
        @DecimalMin(value = "0") BigDecimal feeRate,
        Boolean startInBtc
) {
}
