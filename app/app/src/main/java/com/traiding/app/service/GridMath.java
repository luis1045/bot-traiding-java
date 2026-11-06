package com.traiding.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculos base de la grilla: límites de precio de cada nivel y reparto de porcentajes.
 */
public final class GridMath {

    /** Escala usada para precios y cantidades. */
    public static final int SCALE = 8;

    private GridMath() {
    }

    /**
     * Calcula los límites de la grilla (niveles + 1 límites, empezando en el piso y terminando
     * exactamente en el techo).
     */
    public static List<BigDecimal> boundaries(BigDecimal floor, BigDecimal ceil, int levelCount) {
        BigDecimal step = ceil.subtract(floor)
                .divide(BigDecimal.valueOf(levelCount), SCALE, RoundingMode.HALF_UP);

        List<BigDecimal> result = new ArrayList<>(levelCount + 1);
        for (int k = 0; k <= levelCount; k++) {
            BigDecimal boundary = floor.add(step.multiply(BigDecimal.valueOf(k)))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            result.add(boundary);
        }
        // El último límite debe coincidir exactamente con el techo.
        result.set(levelCount, ceil.setScale(SCALE, RoundingMode.HALF_UP));
        return result;
    }

    /** Reparto equitativo: todos los niveles pesan lo mismo. */
    public static List<BigDecimal> equalPercents(int levelCount) {
        BigDecimal share = BigDecimal.valueOf(100)
                .divide(BigDecimal.valueOf(levelCount), SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> result = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) {
            result.add(share);
        }
        return result;
    }

    /** Suma de los porcentajes. */
    public static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal pct(BigDecimal inventory, BigDecimal percent) {
        return inventory.multiply(percent)
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }
}
