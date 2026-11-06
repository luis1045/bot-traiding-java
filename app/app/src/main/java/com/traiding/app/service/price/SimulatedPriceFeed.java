package com.traiding.app.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Feed simulado: paseo aleatorio alrededor del precio inicial. Sirve para probar el motor
 * sin depender de la red ni de dinero real.
 */
public class SimulatedPriceFeed implements PriceFeed {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPriceFeed.class);

    private final BigDecimal initialPrice;
    private final BigDecimal volatility;
    private final Duration interval;

    public SimulatedPriceFeed(BigDecimal initialPrice, BigDecimal volatility, Duration interval) {
        this.initialPrice = initialPrice;
        this.volatility = volatility;
        this.interval = interval;
    }

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public Flux<BigDecimal> prices() {
        return Flux.interval(interval)
                .scan(initialPrice, (previous, tick) -> next(previous))
                .skip(1)
                .doOnNext(p -> log.debug("precio simulado: {}", p));
    }

    private BigDecimal next(BigDecimal previous) {
        double delta = ThreadLocalRandom.current().nextDouble(-1.0, 1.0) * volatility.doubleValue();
        BigDecimal factor = BigDecimal.valueOf(1.0 + delta);
        BigDecimal value = previous.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        return value.max(BigDecimal.ONE);
    }
}
