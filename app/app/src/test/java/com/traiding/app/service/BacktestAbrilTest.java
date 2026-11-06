package com.traiding.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.traiding.app.web.dto.ResolveRequest;
import com.traiding.app.web.error.BadRequestException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backtest histórico: ¿qué habría ganado la grilla con ~500 USD si hubiera arrancado el 1 de abril?
 *
 * <p>Descarga velas horarias reales de BTCUSDT (Binance público) desde el 1-abr-2026 y las
 * alimenta al motor real de producción ({@link TradingEngine}), registrando al instante cada
 * alerta con los valores sugeridos (como un usuario que ejecuta siempre la operación en cuanto
 * recibe el aviso). Usa {@link AlertService} y {@link GridExecutor}, con comisiones y reinversión
 * por nivel. Si no hay red, el test se omite sin fallar.</p>
 *
 * <p>Supuestos: relleno a los precios horarios (sin slippage), comisión 0.1 % por operación y,
 * si un conjunto se agota, se aplica CONTINUE (como haría el usuario) para seguir operando. Si
 * una compra no cabe en la reserva declarada, el aviso se descarta (no se opera a crédito).</p>
 */
@SpringBootTest
class BacktestAbrilTest {

    /** 1-abr-2026 00:00 UTC en milisegundos. */
    private static final long ABRIL_1 = 1775001600000L;
    private static final int NIVELES = 6;
    private static final BigDecimal COMISION = new BigDecimal("0.001");
    private static final BigDecimal MONTO = new BigDecimal("500");

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

    private record Bar(long openTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    }

    private record Resultado(String nombre, BigDecimal valorFinal, BigDecimal ganancia, BigDecimal roi,
                             int compras, int ventas, int agotados, BigDecimal reserva,
                             BigDecimal btcHeld, BigDecimal realizado, int crecidos) {
    }

    @Test
    void backtestQuinientosDolaresDesdeAbril() throws Exception {
        List<Bar> bars = fetchHourlyBars();
        Assumptions.assumeTrue(bars != null && bars.size() >= 4000,
                "sin datos de Binance: backtest omitido");

        BigDecimal precioAbril = bars.get(0).open();
        BigDecimal precioActual = bars.get(bars.size() - 1).close();
        BigDecimal inventario = MONTO.divide(precioAbril, GridMath.SCALE, RoundingMode.HALF_UP);

        // Ticks por vela: alto y bajo; el orden se alterna para no sesgar el sentido del camino.
        List<BigDecimal> ticks = new ArrayList<>(bars.size() * 2);
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            if (i % 2 == 0) {
                ticks.add(b.low());
                ticks.add(b.high());
            } else {
                ticks.add(b.high());
                ticks.add(b.low());
            }
        }

        List<Resultado> resultados = List.of(
                ejecutar("A) Arranca en BTC + grilla amplia 58000-88000",
                        new BigDecimal("58000"), new BigDecimal("88000"), inventario,
                        BigDecimal.ZERO, true, ticks, precioAbril, precioActual),
                ejecutar("B) Arranca en efectivo 500 USDT + grilla amplia 58000-88000",
                        new BigDecimal("58000"), new BigDecimal("88000"), inventario,
                        MONTO, false, ticks, precioAbril, precioActual),
                ejecutar("C) Arranca en BTC + grilla ajustada de abril 64000-76000",
                        new BigDecimal("64000"), new BigDecimal("76000"), inventario,
                        BigDecimal.ZERO, true, ticks, precioAbril, precioActual));

        BigDecimal comprarYGuardar = MONTO.multiply(precioActual)
                .divide(precioAbril, 2, RoundingMode.HALF_UP);
        BigDecimal roiHold = comprarYGuardar.subtract(MONTO)
                .multiply(BigDecimal.valueOf(100)).divide(MONTO, 2, RoundingMode.HALF_UP);

        String rangoMin = bars.stream().map(Bar::low).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).toPlainString();
        String rangoMax = bars.stream().map(Bar::high).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).toPlainString();

        StringBuilder sb = new StringBuilder();
        sb.append("=== BACKTEST BTC/USDT: 500 USD desde el 1-abril-2026 ===\n");
        sb.append(String.format("Velas horarias: %d (%d ticks por escenario)%n", bars.size(), ticks.size()));
        sb.append(String.format("BTC 1-abril: %s | BTC actual: %s | rango del periodo: %s - %s%n",
                precioAbril, precioActual, rangoMin, rangoMax));
        sb.append(String.format("Inventario: %s BTC (500 USD a precio de abril) | %d niveles | comision 0.1%%%n",
                inventario, NIVELES));
        sb.append("Politica: si el conjunto se agota (precio >= techo) se aplica CONTINUE.\n\n");

        for (Resultado r : resultados) {
            sb.append(r.nombre()).append('\n');
            sb.append(String.format("  valor final      : %s USD  (ganancia %s USD, %s %%)%n",
                    r.valorFinal(), r.ganancia(), r.roi()));
            sb.append(String.format("  operaciones      : %d (%d compras / %d ventas) | agotamientos: %d%n",
                    r.compras() + r.ventas(), r.compras(), r.ventas(), r.agotados()));
            sb.append(String.format("  reserva final    : %s USD%n", r.reserva()));
            sb.append(String.format("  BTC en cartera   : %s BTC (valor %s USD)%n",
                    r.btcHeld(), r.btcHeld().multiply(precioActual).setScale(2, RoundingMode.HALF_UP)));
            sb.append(String.format("  PnL realizado    : %s USD | niveles con cantidad crecida: %d/%d%n%n",
                    r.realizado().setScale(2, RoundingMode.HALF_UP), r.crecidos(), NIVELES));
        }

        sb.append(String.format("Referencia comprar y mantener 500 USD desde abril: %s USD (%s %%)%n",
                comprarYGuardar, roiHold));
        sb.append("Notas: relleno a precios horarios sin slippage; comision 0.1% por operacion; ");
        sb.append("piso/techo elegidos retrospectivamente sobre el rango del periodo.\n");

        String texto = sb.toString();
        System.out.println(texto);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "backtest-abril.txt"), texto);

        for (Resultado r : resultados) {
            assertTrue(r.compras() + r.ventas() > 0, r.nombre() + ": debe haber operado");
            assertTrue(r.valorFinal().signum() > 0, r.nombre() + ": valor final calculable");
            assertTrue(r.reserva().signum() >= 0, r.nombre() + ": la reserva nunca queda negativa");
        }

        // Dejar la base limpia para el resto de la suite.
        alertRepository.deleteAll().block();
        tradeRepository.deleteAll().block();
        levelRepository.deleteAll().block();
        setRepository.deleteAll().block();
    }

    private Resultado ejecutar(String nombre, BigDecimal piso, BigDecimal techo, BigDecimal inventario,
                               BigDecimal reserva, boolean startInBtc, List<BigDecimal> ticks,
                               BigDecimal precioAbril, BigDecimal precioActual) {
        alertRepository.deleteAll().block();
        tradeRepository.deleteAll().block();
        levelRepository.deleteAll().block();
        setRepository.deleteAll().block();
        priceHolder.set(precioAbril);

        CreateGridRequest request = new CreateGridRequest(piso, techo, NIVELES, inventario, reserva,
                null, COMISION, startInBtc);
        GridSet set = gridService.createSet(request).block();
        if (set == null) {
            throw new IllegalStateException("no se pudo crear el conjunto del backtest");
        }
        Long setId = set.getId();
        System.out.printf(">> [%s] %d ticks...%n", nombre, ticks.size());

        int agotados = 0;
        int hechos = 0;
        for (BigDecimal tick : ticks) {
            engine.onPrice(tick).block();
            aprobarPendientes();
            GridSet estado = setRepository.findById(setId).block();
            if (estado != null && GridSetStatus.EXHAUSTED.name().equals(estado.getStatus())) {
                gridService.resolve(setId, new ResolveRequest("CONTINUE", null, null, null, null, null)).block();
                agotados++;
            }
            if (++hechos % 3000 == 0) {
                System.out.printf("   ... %d/%d ticks%n", hechos, ticks.size());
            }
        }

        GridSet finalSet = setRepository.findById(setId).block();
        List<GridLevel> niveles = levelRepository.findByGridSetIdOrderByLevelIndexAsc(setId).collectList().block();
        List<Trade> trades = tradeRepository.findByGridSetIdOrderByCreatedAtDesc(setId).collectList().block();
        if (finalSet == null || niveles == null || trades == null) {
            throw new IllegalStateException("estado final inesperado del backtest");
        }

        int compras = 0;
        int ventas = 0;
        for (Trade trade : trades) {
            if ("BUY".equals(trade.getSide())) {
                compras++;
            } else {
                ventas++;
            }
        }

        BigDecimal cantidadInicial = GridMath.pct(inventario, GridMath.equalPercents(NIVELES).get(0));
        BigDecimal btcHeld = BigDecimal.ZERO;
        BigDecimal realizado = BigDecimal.ZERO;
        int crecidos = 0;
        for (GridLevel level : niveles) {
            if (level.isHolding()) {
                btcHeld = btcHeld.add(level.getBtcAllocated());
            }
            realizado = realizado.add(level.getPnlUsdt());
            if (level.getBtcAllocated().compareTo(cantidadInicial) > 0) {
                crecidos++;
            }
        }

        BigDecimal valorFinal = finalSet.getUsdtReserve()
                .add(btcHeld.multiply(precioActual))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ganancia = valorFinal.subtract(MONTO);
        BigDecimal roi = ganancia.multiply(BigDecimal.valueOf(100))
                .divide(MONTO, 2, RoundingMode.HALF_UP);

        return new Resultado(nombre, valorFinal, ganancia, roi, compras, ventas, agotados,
                finalSet.getUsdtReserve(), btcHeld, realizado, crecidos);
    }

    /**
     * Registra al instante todas las alertas pendientes con los valores sugeridos: equivale a un
     * usuario que ejecuta la operación nada más recibir el aviso. Si la compra no cabe en la
     * reserva declarada, se descarta el aviso (no se opera a crédito).
     */
    private void aprobarPendientes() {
        List<GridAlert> pendientes = alertRepository.findByStatusOrderByIdAsc(AlertService.PENDING)
                .collectList()
                .block();
        if (pendientes == null) {
            return;
        }
        for (GridAlert alerta : pendientes) {
            try {
                if (AlertService.BUY.equals(alerta.getType())) {
                    alertService.approveBuy(alerta.getId(), new ApproveBuyRequest(
                            alerta.getSuggestedQuantity(), alerta.getSuggestedPrice(),
                            "backtest", null)).block();
                } else {
                    alertService.approveSell(alerta.getId(), new ApproveSellRequest(
                            alerta.getSuggestedQuantity(), alerta.getSuggestedPrice(),
                            null, null, "backtest")).block();
                }
            } catch (BadRequestException sinEfectivo) {
                alertService.dismiss(alerta.getId(), "sin efectivo").block();
            }
        }
    }

    /** Velas horarias BTCUSDT desde el 1-abr-2026 hasta ahora; {@code null} si no hay red. */
    private List<Bar> fetchHourlyBars() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            ObjectMapper mapper = new ObjectMapper();
            List<Bar> bars = new ArrayList<>();
            long start = ABRIL_1;
            while (true) {
                String url = "https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1h"
                        + "&startTime=" + start + "&limit=1000";
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                JsonNode arr = mapper.readTree(response.body());
                if (!arr.isArray() || arr.isEmpty()) {
                    break;
                }
                for (JsonNode k : arr) {
                    bars.add(new Bar(k.get(0).asLong(),
                            new BigDecimal(k.get(1).asText()),
                            new BigDecimal(k.get(2).asText()),
                            new BigDecimal(k.get(3).asText()),
                            new BigDecimal(k.get(4).asText())));
                }
                start = arr.get(arr.size() - 1).get(0).asLong() + 3_600_000L;
                if (arr.size() < 1000) {
                    break;
                }
            }
            System.out.printf("Descargadas %d velas horarias (%s -> %s UTC)%n", bars.size(),
                    Instant.ofEpochMilli(ABRIL_1).atZone(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                    bars.isEmpty() ? "?" : Instant.ofEpochMilli(bars.get(bars.size() - 1).openTime())
                            .atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE));
            return bars;
        } catch (Exception e) {
            System.out.println("Backtest omitido, sin acceso a Binance: " + e.getMessage());
            return null;
        }
    }
}
