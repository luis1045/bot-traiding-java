package com.traiding.app.service;

import com.traiding.app.service.price.PriceFeed;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Orquesta el bot: conecta el feed de precios con el {@link TradingEngine}.
 * Se puede arrancar/parar en caliente vía API.
 */
@Component
public class TradingBot {

    private static final Logger log = LoggerFactory.getLogger(TradingBot.class);

    private final PriceFeed priceFeed;
    private final TradingEngine engine;
    private final PriceHolder priceHolder;
    private final com.traiding.app.config.BotProperties properties;

    private volatile Disposable subscription;

    public TradingBot(PriceFeed priceFeed,
                      TradingEngine engine,
                      PriceHolder priceHolder,
                      com.traiding.app.config.BotProperties properties) {
        this.priceFeed = priceFeed;
        this.engine = engine;
        this.priceHolder = priceHolder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.isAutoStart()) {
            start();
        }
    }

    public synchronized boolean start() {
        if (isRunning()) {
            return false;
        }
        log.info("Arrancando bot con feed '{}' para {}", priceFeed.name(), properties.getSymbol());
        subscription = priceFeed.prices()
                .concatMap(price -> Mono.fromRunnable(() -> priceHolder.set(price))
                        .then(engine.onPrice(price)))
                .subscribe(
                        price -> {
                        },
                        error -> log.error("El feed de precios terminó con error", error));
        return true;
    }

    public synchronized boolean stop() {
        if (!isRunning()) {
            return false;
        }
        log.info("Deteniendo bot");
        subscription.dispose();
        subscription = null;
        return true;
    }

    public boolean isRunning() {
        Disposable current = subscription;
        return current != null && !current.isDisposed();
    }

    public String feedName() {
        return priceFeed.name();
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }
}
