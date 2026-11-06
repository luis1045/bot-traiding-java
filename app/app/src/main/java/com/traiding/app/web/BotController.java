package com.traiding.app.web;

import com.traiding.app.config.BotProperties;
import com.traiding.app.domain.GridSetStatus;
import com.traiding.app.repository.GridAlertRepository;
import com.traiding.app.repository.GridSetRepository;
import com.traiding.app.service.AlertService;
import com.traiding.app.service.PriceHolder;
import com.traiding.app.service.TradingBot;
import com.traiding.app.service.TradingEngine;
import com.traiding.app.web.dto.BotStatusResponse;
import com.traiding.app.web.dto.ManualPriceRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/** Control del bot: arranque, parada, estado y precio manual para pruebas. */
@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final TradingBot bot;
    private final TradingEngine engine;
    private final PriceHolder priceHolder;
    private final GridSetRepository setRepository;
    private final GridAlertRepository alertRepository;
    private final BotProperties properties;

    public BotController(TradingBot bot,
                         TradingEngine engine,
                         PriceHolder priceHolder,
                         GridSetRepository setRepository,
                         GridAlertRepository alertRepository,
                         BotProperties properties) {
        this.bot = bot;
        this.engine = engine;
        this.priceHolder = priceHolder;
        this.setRepository = setRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
    }

    @PostMapping("/start")
    public Mono<BotStatusResponse> start() {
        bot.start();
        return status();
    }

    @PostMapping("/stop")
    public Mono<BotStatusResponse> stop() {
        bot.stop();
        return status();
    }

    @GetMapping("/status")
    public Mono<BotStatusResponse> status() {
        return Mono.zip(
                        setRepository.findByStatus(GridSetStatus.ACTIVE.name()).count(),
                        alertRepository.findByStatusOrderByIdAsc(AlertService.PENDING).count())
                .map(totals -> new BotStatusResponse(
                        bot.isRunning(),
                        bot.feedName(),
                        properties.getSymbol(),
                        priceHolder.get(),
                        totals.getT1(),
                        totals.getT2()));
    }

    /** Inyecta un precio manual y lo procesa (útil para probar sin feed en vivo). */
    @PostMapping("/tick")
    public Mono<BotStatusResponse> tick(@Valid @RequestBody ManualPriceRequest request) {
        BigDecimal price = request.price();
        priceHolder.set(price);
        return engine.onPrice(price).then(status());
    }
}
