package com.traiding.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Propiedades del bot, enlazadas desde {@code application.properties} (prefijo {@code bot}). */
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    /** Par operado, por ejemplo BTCUSDT. */
    private String symbol = "BTCUSDT";

    /** Fuente de precios: {@code simulated} o {@code binance}. */
    private String feed = "simulated";

    /** Intervalo de muestreo del precio en milisegundos. */
    private long pollIntervalMs = 2000;

    /** Comisión por operación (0.001 = 0.1%). */
    private BigDecimal feeRate = new BigDecimal("0.001");

    /** Precio inicial del simulador. */
    private BigDecimal initialPrice = new BigDecimal("83000");

    /** Volatilidad por tick del simulador (0.003 = 0.3%). */
    private BigDecimal simulatedVolatility = new BigDecimal("0.003");

    /** Arrancar el bot automáticamente al levantar la aplicación. */
    private boolean autoStart = false;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getFeed() {
        return feed;
    }

    public void setFeed(String feed) {
        this.feed = feed;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    public void setInitialPrice(BigDecimal initialPrice) {
        this.initialPrice = initialPrice;
    }

    public BigDecimal getSimulatedVolatility() {
        return simulatedVolatility;
    }

    public void setSimulatedVolatility(BigDecimal simulatedVolatility) {
        this.simulatedVolatility = simulatedVolatility;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
}
