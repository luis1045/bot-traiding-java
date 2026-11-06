package com.traiding.app.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridMathTest {

    @Test
    void calculaLimitesLinealesEntrePisoYTecho() {
        List<BigDecimal> boundaries = GridMath.boundaries(
                new BigDecimal("80000"), new BigDecimal("90000"), 5);

        assertEquals(6, boundaries.size());
        assertEquals(0, boundaries.get(0).compareTo(new BigDecimal("80000")));
        assertEquals(0, boundaries.get(1).compareTo(new BigDecimal("82000")));
        assertEquals(0, boundaries.get(2).compareTo(new BigDecimal("84000")));
        assertEquals(0, boundaries.get(3).compareTo(new BigDecimal("86000")));
        assertEquals(0, boundaries.get(4).compareTo(new BigDecimal("88000")));
        // El último límite es exactamente el techo.
        assertEquals(0, boundaries.get(5).compareTo(new BigDecimal("90000")));
    }

    @Test
    void repartoEquitativoSuma100() {
        List<BigDecimal> percents = GridMath.equalPercents(4);
        assertEquals(4, percents.size());
        assertEquals(0, GridMath.sum(percents).compareTo(new BigDecimal("100")));
    }

    @Test
    void reparteInventarioSegunPorcentaje() {
        BigDecimal allocated = GridMath.pct(new BigDecimal("0.5"), new BigDecimal("20"));
        assertEquals(0, allocated.compareTo(new BigDecimal("0.1")));
    }

    @Test
    void ultimoLimiteEsTechoAunqueElPasoNoSeaExacto() {
        List<BigDecimal> boundaries = GridMath.boundaries(
                new BigDecimal("100"), new BigDecimal("200"), 3);
        assertTrue(boundaries.get(boundaries.size() - 1).compareTo(new BigDecimal("200")) == 0);
    }
}
