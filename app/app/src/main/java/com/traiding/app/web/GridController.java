package com.traiding.app.web;

import com.traiding.app.service.AlertService;
import com.traiding.app.service.GridService;
import com.traiding.app.service.ReportCsv;
import com.traiding.app.web.dto.CreateGridRequest;
import com.traiding.app.web.dto.GridAlertResponse;
import com.traiding.app.web.dto.GridLevelResponse;
import com.traiding.app.web.dto.GridReportResponse;
import com.traiding.app.web.dto.GridSetResponse;
import com.traiding.app.web.dto.PnlResponse;
import com.traiding.app.web.dto.ResolveRequest;
import com.traiding.app.web.dto.TradeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** API de los conjuntos de niveles (grillas). */
@RestController
@RequestMapping("/api/grids")
public class GridController {

    private final GridService gridService;
    private final AlertService alertService;

    public GridController(GridService gridService, AlertService alertService) {
        this.gridService = gridService;
        this.alertService = alertService;
    }

    /** Crea un conjunto de niveles a partir del piso, techo, n y los porcentajes por nivel. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<GridSetResponse> create(@Valid @RequestBody CreateGridRequest request) {
        return gridService.createSet(request).map(GridSetResponse::from);
    }

    @GetMapping
    public Flux<GridSetResponse> list() {
        return gridService.listSets().map(GridSetResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<GridSetResponse> get(@PathVariable Long id) {
        return gridService.getSet(id).map(GridSetResponse::from);
    }

    @GetMapping("/{id}/levels")
    public Flux<GridLevelResponse> levels(@PathVariable Long id) {
        return gridService.levels(id).map(GridLevelResponse::from);
    }

    @GetMapping("/{id}/trades")
    public Flux<TradeResponse> trades(@PathVariable Long id) {
        return gridService.trades(id).map(TradeResponse::from);
    }

    /** Alertas (de compra/venta) del conjunto, de la más antigua a la más reciente. */
    @GetMapping("/{id}/alerts")
    public Flux<GridAlertResponse> alerts(@PathVariable Long id) {
        return alertService.bySet(id);
    }

    /** Ganancia consolidada del conjunto, en BTC y en USDT. */
    @GetMapping("/{id}/pnl")
    public Mono<PnlResponse> pnl(@PathVariable Long id) {
        return gridService.pnl(id);
    }

    /** Reporte de ganancias por nivel: PnL realizado + no realizado (mark-to-market). */
    @GetMapping("/{id}/report")
    public Mono<GridReportResponse> report(@PathVariable Long id) {
        return gridService.report(id);
    }

    /** El mismo reporte en CSV, para descargar. */
    @GetMapping(value = "/{id}/report.csv", produces = "text/csv")
    public Mono<ResponseEntity<String>> reportCsv(@PathVariable Long id) {
        return gridService.report(id).map(report -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reporte-conjunto-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(ReportCsv.toCsv(report)));
    }

    /** Con el conjunto agotado: CONTINUE (mismos niveles) o NEW_SET (nuevo piso/techo). */
    @PostMapping("/{id}/resolve")
    public Mono<GridSetResponse> resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request) {
        return gridService.resolve(id, request).map(GridSetResponse::from);
    }

    /** Elimina un conjunto, sus niveles y su historial de operaciones. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return gridService.deleteSet(id);
    }
}
