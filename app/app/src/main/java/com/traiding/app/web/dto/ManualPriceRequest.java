package com.traiding.app.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Precio manual para probar el motor sin feed en vivo. */
public record ManualPriceRequest(
        @NotNull BigDecimal price
) {
}
