package com.traiding.app.config;

import com.traiding.app.service.price.BinancePriceFeed;
import com.traiding.app.service.price.PriceFeed;
import com.traiding.app.service.price.SimulatedPriceFeed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Locale;

/** Selecciona la fuente de precios según la propiedad {@code bot.feed}. */
@Configuration
public class PriceFeedConfiguration {

    /** Cliente compartido hacia la API pública de Binance (gratuita, sin API keys). */
    @Bean
    public WebClient binanceWebClient() {
        return WebClient.builder().baseUrl("https://api.binance.com").build();
    }

    @Bean
    public PriceFeed priceFeed(BotProperties properties, WebClient binanceWebClient) {
        String feed = properties.getFeed() == null ? "simulated" : properties.getFeed().toLowerCase(Locale.ROOT);
        Duration interval = Duration.ofMillis(properties.getPollIntervalMs());

        return switch (feed) {
            case "binance" -> new BinancePriceFeed(binanceWebClient, properties.getSymbol(), interval);
            default -> new SimulatedPriceFeed(
                    properties.getInitialPrice(),
                    properties.getSimulatedVolatility(),
                    interval);
        };
    }
}
