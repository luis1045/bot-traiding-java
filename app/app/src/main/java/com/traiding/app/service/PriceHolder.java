package com.traiding.app.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/** Guarda el último precio conocido del par para consultarlo sin suscribirse al feed. */
@Component
public class PriceHolder {

    private final AtomicReference<BigDecimal> last = new AtomicReference<>();

    public void set(BigDecimal price) {
        if (price != null) {
            last.set(price);
        }
    }

    public BigDecimal get() {
        return last.get();
    }

    public BigDecimal getOrElse(BigDecimal fallback) {
        BigDecimal value = last.get();
        return value != null ? value : fallback;
    }
}
