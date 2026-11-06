package com.traiding.app.service;

import com.traiding.app.domain.GridAlert;
import com.traiding.app.domain.GridLevel;
import com.traiding.app.domain.GridSet;
import com.traiding.app.domain.GridSetStatus;
import com.traiding.app.domain.Trade;
import com.traiding.app.repository.GridAlertRepository;
import com.traiding.app.repository.GridLevelRepository;
import com.traiding.app.repository.GridSetRepository;
import com.traiding.app.repository.TradeRepository;
import com.traiding.app.web.dto.ApproveBuyRequest;
import com.traiding.app.web.dto.ApproveSellRequest;
import com.traiding.app.web.dto.CreateGridRequest;
import com.traiding.app.web.dto.GridReportResponse;
import com.traiding.app.web.dto.LevelReportResponse;
import com.traiding.app.web.dto.PnlResponse;
import com.traiding.app.web.dto.ResolveRequest;
import com.traiding.app.web.error.BadRequestException;
import com.traiding.app.web.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujo completo del modo manual: el motor solo genera alertas de zona; las operaciones se
 * registran cuando el usuario envía la cantidad, el precio y (en las ventas) el profit.
 */
@SpringBootTest
class GridTradingIntegrationTest {

    @Autowired
    private GridService gridService;

    @Autowired
    private TradingEngine engine;

    @Autowired
    private AlertService alertService;

    @Autowired
    private GridSetRepository setRepository;

    @Autowired
    private GridLevelRepository levelRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private GridAlertRepository alertRepository;

    @Autowired
    private PriceHolder priceHolder;

    @BeforeEach
    void cleanDatabase() {
        alertRepository.deleteAll().block();
        tradeRepository.deleteAll().block();
        levelRepository.deleteAll().block();
        setRepository.deleteAll().block();
    }

    @Test
    void creaConjuntoYGuardaNivelesEnBaseDeDatos() {
        GridSet set = gridService.createSet(request()).block();

        assertNotNull(set);
        assertNotNull(set.getId(), "el id debe generarse en la base de datos");
        assertEquals(GridSetStatus.ACTIVE.name(), set.getStatus());
        assertEquals(5, set.getLevelCount());

        List<GridLevel> levels = levelRepository
                .findByGridSetIdOrderByLevelIndexAsc(set.getId())
                .collectList()
                .block();

        assertNotNull(levels);
        assertEquals(5, levels.size());
        assertTrue(levels.stream().allMatch(l -> l.getId() != null));

        GridLevel first = levels.get(0);
        assertEquals(0, first.getLowerPrice().compareTo(new BigDecimal("80000")));
        assertEquals(0, first.getUpperPrice().compareTo(new BigDecimal("82000")));
        // 20% de 0.5 BTC = 0.1 BTC por nivel
        assertEquals(0, first.getBtcAllocated().compareTo(new BigDecimal("0.1")));
        assertTrue(first.isHolding(), "arrancamos con el BTC en mano");
    }

    @Test
    void alSuperarElTechoAvisaDeVentaYAlAprobarlasMarcaAgotado() {
        GridSet set = gridService.createSet(request()).block();
        assertNotNull(set);

        engine.onPrice(new BigDecimal("91000")).block();

        // El motor NO opera solo: genera un aviso de venta por nivel.
        assertEquals(0L, tradeRepository.findByGridSetIdOrderByCreatedAtDesc(set.getId()).count().block());
        List<GridAlert> pendientes = pendingAlerts();
        assertEquals(5, pendientes.size());
        assertTrue(pendientes.stream().allMatch(a -> AlertService.SELL.equals(a.getType())));

        List<GridLevel> antesDeAprobar = levels(set.getId());
        assertTrue(antesDeAprobar.stream().allMatch(GridLevel::isHolding),
                "sin aprobación manual el BTC sigue en el nivel");

        approveAllPending();

        GridSet reloaded = setRepository.findById(set.getId()).block();
        assertNotNull(reloaded);
        assertEquals(GridSetStatus.EXHAUSTED.name(), reloaded.getStatus());
        assertNotNull(reloaded.getExhaustedAt());

        List<GridLevel> levels = levels(set.getId());
        assertTrue(levels.stream().noneMatch(GridLevel::isHolding), "todo el BTC debe estar vendido");

        long sells = tradeRepository.findByGridSetIdOrderByCreatedAtDesc(set.getId()).count().block();
        assertEquals(5, sells);

        PnlResponse pnl = gridService.pnl(set.getId()).block();
        assertNotNull(pnl);
        assertTrue(pnl.pnlUsdt().signum() > 0, "debe haber ganancia en USDT");
        assertTrue(pnl.pnlBtc().signum() > 0, "debe haber ganancia en BTC");
        assertEquals(0, pnl.btcHeld().compareTo(BigDecimal.ZERO));
    }

    @Test
    void continuarReanudaYMismosNivelesVuelvenAComprar() {
        GridSet set = gridService.createSet(request()).block();
        assertNotNull(set);
        engine.onPrice(new BigDecimal("91000")).block();
        approveAllPending();

        // El usuario decide continuar con los mismos niveles.
        GridSet resumed = gridService.resolve(set.getId(), new ResolveRequest(
                "CONTINUE", null, null, null, null, null)).block();

        assertNotNull(resumed);
        assertEquals(GridSetStatus.ACTIVE.name(), resumed.getStatus());
        assertEquals(0, resumed.getCycleSells());

        // El precio cae por debajo del piso: se avisa de compra en todos los niveles.
        engine.onPrice(new BigDecimal("79000")).block();
        List<GridAlert> avisosCompra = pendingAlerts();
        assertEquals(5, avisosCompra.size());
        assertTrue(avisosCompra.stream().allMatch(a -> AlertService.BUY.equals(a.getType())));

        approveAllPending();
        assertTrue(levels(set.getId()).stream().allMatch(GridLevel::isHolding));

        // Al volver a subir por encima del techo, se agota de nuevo.
        engine.onPrice(new BigDecimal("91000")).block();
        approveAllPending();

        GridSet again = setRepository.findById(set.getId()).block();
        assertNotNull(again);
        assertEquals(GridSetStatus.EXHAUSTED.name(), again.getStatus());
    }

    @Test
    void rechazaPorcentajesQueNoSuman100() {
        CreateGridRequest invalid = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 3,
                new BigDecimal("0.3"), new BigDecimal("1000"),
                List.of(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10")),
                new BigDecimal("0.001"), true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> gridService.createSet(invalid).block());
        assertTrue(error.getMessage().contains("100%"));
    }

    @Test
    void rechazaPisoMayorQueTecho() {
        CreateGridRequest invalid = new CreateGridRequest(
                new BigDecimal("90000"), new BigDecimal("80000"), 3,
                new BigDecimal("0.3"), new BigDecimal("1000"), null, null, true);

        assertThrows(BadRequestException.class, () -> gridService.createSet(invalid).block());
    }

    @Test
    void sinReservaAvisaDeCompraPeroExigeConfirmarElDescubierto() {
        // Sin reserva de USDT: el aviso de compra llega, pero el registro exige confirmación.
        CreateGridRequest sinEfectivo = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 2,
                new BigDecimal("0.2"), BigDecimal.ZERO, null, new BigDecimal("0.001"), false);

        GridSet set = gridService.createSet(sinEfectivo).block();
        assertNotNull(set);

        engine.onPrice(new BigDecimal("79000")).block();

        List<GridAlert> pendientes = pendingAlerts();
        assertEquals(2, pendientes.size());
        assertTrue(pendientes.stream().allMatch(a -> AlertService.BUY.equals(a.getType())));
        assertFalse(levels(set.getId()).stream().anyMatch(GridLevel::isHolding),
                "sin aprobación no se compra nada");

        GridAlert alert = pendientes.get(0);
        BadRequestException error = assertThrows(BadRequestException.class, () ->
                alertService.approveBuy(alert.getId(), new ApproveBuyRequest(
                        alert.getSuggestedQuantity(), alert.getSuggestedPrice(), "test", null)).block());
        assertTrue(error.getMessage().contains("reserva"));

        Trade trade = alertService.approveBuy(alert.getId(), new ApproveBuyRequest(
                alert.getSuggestedQuantity(), alert.getSuggestedPrice(), "test", true)).block();
        assertNotNull(trade);

        GridSet reloaded = setRepository.findById(set.getId()).block();
        assertNotNull(reloaded);
        assertTrue(reloaded.getUsdtReserve().signum() < 0,
                "con confirmación explícita la reserva puede quedar negativa");
    }

    @Test
    void reporteDeGananciasDesglosaPorNivel() {
        // Arranca en efectivo: así las compras y las ventas son deterministas.
        CreateGridRequest inCash = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 5,
                new BigDecimal("0.5"), new BigDecimal("100000"),
                null, new BigDecimal("0.001"), false);

        GridSet set = gridService.createSet(inCash).block();
        assertNotNull(set);

        // El precio cae por debajo del piso: se avisa de compra en los 5 niveles.
        engine.onPrice(new BigDecimal("78000")).block();
        assertEquals(5, pendingAlerts().size());
        approveAllPending();

        // Sube a 83000: solo el nivel 1 (banda 80000-82000) supera su techo y avisa de venta.
        engine.onPrice(new BigDecimal("83000")).block();
        List<GridAlert> avisos = pendingAlerts();
        assertEquals(1, avisos.size());
        assertEquals(AlertService.SELL, avisos.get(0).getType());
        assertEquals(1, avisos.get(0).getLevelIndex());
        approveAllPending();

        priceHolder.set(new BigDecimal("83000"));

        GridReportResponse report = gridService.report(set.getId()).block();
        assertNotNull(report);
        assertEquals(set.getId(), report.gridSetId());
        assertEquals(5, report.levels().size());
        assertEquals(0, report.currentPrice().compareTo(new BigDecimal("83000")));

        LevelReportResponse level1 = report.levels().get(0);
        assertEquals(1, level1.buys());
        assertEquals(1, level1.sells());
        assertFalse(level1.holding());
        assertTrue(level1.realizedPnlUsdt().signum() > 0,
                "compró a 78000 y vendió a 83000: debe haber ganancia realizada");
        assertEquals(0, level1.unrealizedPnlUsdt().compareTo(BigDecimal.ZERO));

        LevelReportResponse level2 = report.levels().get(1);
        assertEquals(1, level2.buys());
        assertEquals(0, level2.sells());
        assertTrue(level2.holding());
        assertTrue(level2.unrealizedPnlUsdt().signum() > 0,
                "sigue en cartera comprado a 78000 y el precio es 83000");

        // La ganancia realizada total es la del único nivel que vendió.
        assertEquals(0, report.realizedPnlUsdt().compareTo(level1.realizedPnlUsdt()));
        assertEquals(0, report.totalPnlUsdt().compareTo(
                report.realizedPnlUsdt().add(report.unrealizedPnlUsdt())));
        assertEquals(6, report.totalTrades());
        assertEquals(5, report.levelsTraded());

        String csv = ReportCsv.toCsv(report);
        assertTrue(csv.contains("Nivel"));
        assertTrue(csv.contains("TOTAL"));
        assertEquals(7, csv.lines().count()); // cabecera + 5 niveles + fila de totales
    }

    @Test
    void eliminaConjuntoConSusNivelesOperacionesYAlertas() {
        GridSet set = gridService.createSet(request()).block();
        assertNotNull(set);

        // El precio supera el techo: avisos de venta que el usuario registra (5 operaciones).
        engine.onPrice(new BigDecimal("91000")).block();
        approveAllPending();
        assertTrue(tradeRepository.findByGridSetIdOrderByCreatedAtDesc(set.getId()).count().block() > 0);
        assertTrue(alertRepository.findByGridSetIdOrderByIdAsc(set.getId()).count().block() > 0);

        gridService.deleteSet(set.getId()).block();

        assertThrows(NotFoundException.class, () -> gridService.getSet(set.getId()).block());
        assertNull(setRepository.findById(set.getId()).block());
        assertEquals(0L, levelRepository.findByGridSetIdOrderByLevelIndexAsc(set.getId()).count().block());
        assertEquals(0L, tradeRepository.findByGridSetIdOrderByCreatedAtDesc(set.getId()).count().block());
        assertEquals(0L, alertRepository.findByGridSetIdOrderByIdAsc(set.getId()).count().block());
    }

    @Test
    void noSeAgotaSiAlgunNivelNuncaCompro() {
        // Arranca en efectivo con 3 niveles, pero el usuario solo registra la compra del nivel 1.
        CreateGridRequest inCash = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 3,
                new BigDecimal("0.3"), new BigDecimal("100000"),
                null, new BigDecimal("0.001"), false);

        GridSet set = gridService.createSet(inCash).block();
        assertNotNull(set);

        engine.onPrice(new BigDecimal("79000")).block();
        List<GridAlert> compras = pendingAlerts();
        assertEquals(3, compras.size());

        // Solo se registra la compra del nivel 1; los otros dos siguen esperando.
        GridAlert nivel1 = compras.stream()
                .filter(a -> Integer.valueOf(1).equals(a.getLevelIndex()))
                .findFirst()
                .orElseThrow();
        alertService.approveBuy(nivel1.getId(), new ApproveBuyRequest(
                nivel1.getSuggestedQuantity(), nivel1.getSuggestedPrice(), "solo nivel 1", null)).block();

        // Sube por encima del techo y vende solo el nivel 1.
        engine.onPrice(new BigDecimal("91000")).block();
        List<GridAlert> ventas = pendingAlerts().stream()
                .filter(a -> AlertService.SELL.equals(a.getType()))
                .toList();
        assertEquals(1, ventas.size());

        GridAlert venta1 = ventas.get(0);
        alertService.approveSell(venta1.getId(), new ApproveSellRequest(
                venta1.getSuggestedQuantity(), venta1.getSuggestedPrice(), null, null, "solo nivel 1")).block();

        // Los niveles 2 y 3 nunca vendieron: el conjunto no puede darse por agotado.
        GridSet reloaded = setRepository.findById(set.getId()).block();
        assertNotNull(reloaded);
        assertEquals(GridSetStatus.ACTIVE.name(), reloaded.getStatus(),
                "quedan niveles que no completaron su ciclo");
        assertNull(reloaded.getExhaustedAt());

        // Y sus avisos de compra siguen pendientes (no se descartan por un agotamiento falso).
        List<GridAlert> pendientes = pendingAlerts();
        assertEquals(2, pendientes.size());
        assertTrue(pendientes.stream().allMatch(a -> AlertService.BUY.equals(a.getType())));
        assertTrue(pendientes.stream().map(GridAlert::getLevelIndex).toList().containsAll(List.of(2, 3)));
    }

    @Test
    void laCompraManualFijaLaCantidadRealDelNivel() {
        CreateGridRequest inCash = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 2,
                new BigDecimal("0.2"), new BigDecimal("100000"),
                null, new BigDecimal("0.001"), false);

        GridSet set = gridService.createSet(inCash).block();
        assertNotNull(set);

        engine.onPrice(new BigDecimal("79000")).block();
        GridAlert compra = pendingAlerts().stream()
                .filter(a -> Integer.valueOf(1).equals(a.getLevelIndex()))
                .findFirst()
                .orElseThrow();

        // El usuario compró menos de lo sugerido: manda su cantidad real.
        alertService.approveBuy(compra.getId(), new ApproveBuyRequest(
                new BigDecimal("0.05"), new BigDecimal("79000"), "compra parcial", null)).block();

        GridLevel nivel1 = levels(set.getId()).get(0);
        assertTrue(nivel1.isHolding());
        assertEquals(0, nivel1.getBtcAllocated().compareTo(new BigDecimal("0.05")));
        assertEquals(0, nivel1.getEntryPrice().compareTo(new BigDecimal("79000")));

        // El aviso de venta posterior sugiere la cantidad realmente comprada.
        engine.onPrice(new BigDecimal("86000")).block();
        GridAlert venta = pendingAlerts().stream()
                .filter(a -> AlertService.SELL.equals(a.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, venta.getSuggestedQuantity().compareTo(new BigDecimal("0.05")));
    }

    @Test
    void reinvierteLaGananciaEnLaCantidadDelNivel() {
        GridSet set = gridService.createSet(request()).block();
        assertNotNull(set);

        BigDecimal original = levels(set.getId()).get(0).getBtcAllocated();
        assertEquals(0, original.compareTo(new BigDecimal("0.1")), "reparto equitativo: 0.1 BTC por nivel");

        // 1) Cierre del techo: se avisa a los 5 niveles y el usuario registra sus ventas.
        engine.onPrice(new BigDecimal("91000")).block();
        approveAllPending();

        List<GridLevel> trasVender = levels(set.getId());
        assertTrue(trasVender.stream().noneMatch(GridLevel::isHolding));

        GridLevel l1 = trasVender.get(0);
        assertTrue(l1.getBtcAllocated().compareTo(original) > 0,
                "la cantidad del nivel debe crecer con la ganancia reinvertida");

        // La nueva cantidad = recaudado de la venta / (piso * (1 + comisión)).
        BigDecimal fee = set.getFeeRate();
        BigDecimal proceeds = new BigDecimal("91000")
                .multiply(original)
                .multiply(BigDecimal.ONE.subtract(fee));
        BigDecimal expected = proceeds.divide(
                l1.getLowerPrice().multiply(BigDecimal.ONE.add(fee)),
                GridMath.SCALE, RoundingMode.HALF_UP);
        assertEquals(0, l1.getBtcAllocated().compareTo(expected));

        // 2) Reanudar y volver a comprar: la compra opera con la cantidad ampliada.
        gridService.resolve(set.getId(), new ResolveRequest(
                "CONTINUE", null, null, null, null, null)).block();
        engine.onPrice(new BigDecimal("79000")).block();
        assertEquals(5, pendingAlerts().size(), "un aviso de compra por nivel");
        approveAllPending();

        List<GridLevel> trasComprar = levels(set.getId());
        assertTrue(trasComprar.stream().allMatch(GridLevel::isHolding));

        List<Trade> trades = tradeRepository
                .findByGridSetIdOrderByCreatedAtDesc(set.getId())
                .collectList()
                .block();
        assertNotNull(trades);
        Trade buyL1 = trades.stream()
                .filter(t -> "BUY".equals(t.getSide()) && Integer.valueOf(1).equals(t.getLevelIndex()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, buyL1.getQuantity().compareTo(trasComprar.get(0).getBtcAllocated()),
                "la compra debe operar con la cantidad ampliada");

        GridSet reloaded = setRepository.findById(set.getId()).block();
        assertNotNull(reloaded);
        assertTrue(reloaded.getUsdtReserve().signum() >= 0,
                "la reserva nunca debe quedar negativa con la reinversión");

        // 3) Nuevo cierre del techo: la cantidad vuelve a crecer (compounding por nivel).
        engine.onPrice(new BigDecimal("91000")).block();
        approveAllPending();

        GridLevel l1Final = levels(set.getId()).get(0);
        assertTrue(l1Final.getBtcAllocated().compareTo(trasVender.get(0).getBtcAllocated()) > 0,
                "cada ciclo reinvierte la ganancia y la cantidad sigue creciendo");
    }

    @Test
    void descartarUnaAlertaNoLaRepiteHastaSalirDeLaZona() {
        CreateGridRequest inCash = new CreateGridRequest(
                new BigDecimal("80000"), new BigDecimal("90000"), 3,
                new BigDecimal("0.3"), new BigDecimal("100000"),
                null, new BigDecimal("0.001"), false);

        GridSet set = gridService.createSet(inCash).block();
        assertNotNull(set);

        engine.onPrice(new BigDecimal("79000")).block();
        List<GridAlert> pendientes = pendingAlerts();
        assertEquals(3, pendientes.size());

        GridAlert primera = pendientes.get(0);
        alertService.dismiss(primera.getId(), "lo hago luego").block();
        assertEquals(2, pendingAlerts().size());

        // El precio sigue en la zona: no se repite el aviso descartado.
        engine.onPrice(new BigDecimal("79000")).block();
        assertEquals(2, pendingAlerts().size());

        // Sale de la zona del nivel 1 (80000-83333) y vuelve a entrar: nuevo aviso.
        engine.onPrice(new BigDecimal("81000")).block();
        engine.onPrice(new BigDecimal("79000")).block();

        List<GridAlert> despues = pendingAlerts();
        assertEquals(3, despues.size());
        assertTrue(despues.stream().anyMatch(a ->
                        Integer.valueOf(1).equals(a.getLevelIndex()) && AlertService.BUY.equals(a.getType())),
                "al salir y volver a la zona se genera un aviso nuevo");
    }

    // ------------------------------------------------------------------ utilidades

    private List<GridLevel> levels(Long setId) {
        return levelRepository.findByGridSetIdOrderByLevelIndexAsc(setId).collectList().block();
    }

    private List<GridAlert> pendingAlerts() {
        return alertRepository.findByStatusOrderByIdAsc(AlertService.PENDING).collectList().block();
    }

    /** Simula al usuario registrando al instante todas las alertas pendientes. */
    private void approveAllPending() {
        List<GridAlert> pendientes = pendingAlerts();
        if (pendientes == null) {
            return;
        }
        for (GridAlert alert : pendientes) {
            if (AlertService.BUY.equals(alert.getType())) {
                alertService.approveBuy(alert.getId(), new ApproveBuyRequest(
                        alert.getSuggestedQuantity(), alert.getSuggestedPrice(), "test", null)).block();
            } else {
                alertService.approveSell(alert.getId(), new ApproveSellRequest(
                        alert.getSuggestedQuantity(), alert.getSuggestedPrice(),
                        null, null, "test")).block();
            }
        }
    }

    private CreateGridRequest request() {
        return new CreateGridRequest(
                new BigDecimal("80000"),
                new BigDecimal("90000"),
                5,
                new BigDecimal("0.5"),
                new BigDecimal("100000"),
                null,                 // reparto equitativo (20% por nivel)
                new BigDecimal("0.001"),
                true);
    }
}
