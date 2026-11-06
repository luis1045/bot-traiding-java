package com.traiding.app.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Feed con precios reales de Binance vía REST público
 * ({@code GET /api/v3/ticker/price}); no requiere API keys.
 * La ejecución de las órdenes sigue siendo simulada (paper trading).
 */
public class BinancePriceFeed implements PriceFeed {

    private static final Logger log = LoggerFactory.getLogger(BinancePriceFeed.class);

    private final WebClient webClient;
    private final String symbol;
    private final Duration interval;

    public BinancePriceFeed(WebClient webClient, String symbol, Duration interval) {
        this.webClient = webClient;
        this.symbol = symbol;
        this.interval = interval;
    }

    @Override
    public String name() {
        return "binance";
    }

    @Override
    public Flux<BigDecimal> prices() {
        return Flux.interval(interval)
                .concatMap(tick -> fetch())
                .onErrorContinue((error, value) -> log.warn("error leyendo precio de Binance: {}", error.getMessage()));
    }

    private Mono<BigDecimal> fetch() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/ticker/price")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .bodyToMono(BinanceTicker.class)
                .map(ticker -> new BigDecimal(ticker.price()))
                .doOnNext(price -> log.debug("precio Binance {}: {}", symbol, price))
                .onErrorResume(error -> {
                    log.warn("no se pudo obtener el precio de Binance: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
