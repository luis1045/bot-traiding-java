package com.traiding.app.web;

import com.traiding.app.service.AlertService;
import com.traiding.app.web.dto.ApproveBuyRequest;
import com.traiding.app.web.dto.ApproveSellRequest;
import com.traiding.app.web.dto.GridAlertResponse;
import com.traiding.app.web.dto.TradeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Alertas de zona de compra/venta y registro manual de las operaciones.
 *
 * <p>El motor solo avisa; la compra o la venta se graba cuando el usuario envía la cantidad, el
 * precio y (en las ventas) el profit que ha obtenido.</p>
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** Alertas pendientes de todos los conjuntos, en orden de creación. */
    @GetMapping("/pending")
    public Flux<GridAlertResponse> pending() {
        return alertService.pending();
    }

    /** Registra la compra manual de una alerta de zona de compra. */
    @PostMapping("/{id}/approve-buy")
    public Mono<TradeResponse> approveBuy(@PathVariable Long id,
                                          @Valid @RequestBody ApproveBuyRequest request) {
        return alertService.approveBuy(id, request).map(TradeResponse::from);
    }

    /** Registra la venta manual (cantidad, precio y profit) de una alerta de zona de venta. */
    @PostMapping("/{id}/approve-sell")
    public Mono<TradeResponse> approveSell(@PathVariable Long id,
                                           @Valid @RequestBody ApproveSellRequest request) {
        return alertService.approveSell(id, request).map(TradeResponse::from);
    }

    /** Descarta la alerta sin registrar operación. */
    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> dismiss(@PathVariable Long id,
                              @RequestParam(required = false) String note) {
        return alertService.dismiss(id, note).then();
    }
}
