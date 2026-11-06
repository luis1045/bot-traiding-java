package com.traiding.app.web;

import com.traiding.app.config.BotProperties;
import com.traiding.app.service.price.BinanceTicker;
import com.traiding.app.web.dto.MarketPriceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * Precio público de mercado consultando la API gratuita de Binance
 * ({@code GET https://api.binance.com/api/v3/ticker/price}). No requiere API keys
 * y sirve para mostrar el precio real aunque el bot esté detenido.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final WebClient binanceWebClient;
    private final BotProperties properties;

    public MarketController(WebClient binanceWebClient, BotProperties properties) {
        this.binanceWebClient = binanceWebClient;
        this.properties = properties;
    }

    /** Último precio spot del par en Binance. Por defecto usa {@code bot.symbol} (BTCUSDT). */
    @GetMapping("/price")
    public Mono<MarketPriceResponse> price(@RequestParam(name = "symbol", required = false) String symbol) {
        String pair = (symbol == null || symbol.isBlank())
                ? properties.getSymbol()
                : symbol.toUpperCase(Locale.ROOT);

        return binanceWebClient.get()
                .uri(uri -> uri.path("/api/v3/ticker/price").queryParam("symbol", pair).build())
                .retrieve()
                .bodyToMono(BinanceTicker.class)
                .map(ticker -> new MarketPriceResponse(
                        ticker.symbol(),
                        new BigDecimal(ticker.price()),
                        "binance",
                        Instant.now()));
    }
}
