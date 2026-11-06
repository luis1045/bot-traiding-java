package com.traiding.app.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

/**
 * Un nivel (banda) de la grilla.
 *
 * <p>Rango {@code [lowerPrice, upperPrice]}. Se compra al tocar el borde inferior y se vende
 * al tocar el superior, repitiendo ciclos. {@code btcAllocated} es la cantidad de BTC asignada
 * al nivel (inicialmente reparte el inventario según {@code weightPercent}); al vender, la
 * ganancia se reinvierte y esta cantidad crece con el PnL de cada ciclo.</p>
 *
 * <p>{@code holding = true} significa que el nivel tiene BTC listo para vender;
 * {@code holding = false} significa que está en efectivo, esperando comprar en el piso.</p>
 */
@Table("grid_level")
public class GridLevel {

    @Id
    @Column("id")
    private Long id;

    @Column("grid_set_id")
    private Long gridSetId;

    @Column("level_index")
    private Integer levelIndex;

    @Column("lower_price")
    private BigDecimal lowerPrice;

    @Column("upper_price")
    private BigDecimal upperPrice;

    @Column("weight_percent")
    private BigDecimal weightPercent;

    @Column("btc_allocated")
    private BigDecimal btcAllocated;

    @Column("holding")
    private boolean holding;

    @Column("entry_price")
    private BigDecimal entryPrice;

    @Column("btc_bought")
    private BigDecimal btcBought = BigDecimal.ZERO;

    @Column("usdt_spent")
    private BigDecimal usdtSpent = BigDecimal.ZERO;

    @Column("btc_sold")
    private BigDecimal btcSold = BigDecimal.ZERO;

    @Column("usdt_received")
    private BigDecimal usdtReceived = BigDecimal.ZERO;

    @Column("pnl_usdt")
    private BigDecimal pnlUsdt = BigDecimal.ZERO;

    @Column("pnl_btc")
    private BigDecimal pnlBtc = BigDecimal.ZERO;

    @Column("trade_count")
    private int tradeCount;

    /**
     * Ventas completadas desde el último CONTINUE. El conjunto solo se considera agotado cuando
     * todos los niveles han vendido al menos una vez en el ciclo actual.
     */
    @Column("cycle_sells")
    private int cycleSells;

    public GridLevel() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGridSetId() {
        return gridSetId;
    }

    public void setGridSetId(Long gridSetId) {
        this.gridSetId = gridSetId;
    }

    public Integer getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(Integer levelIndex) {
        this.levelIndex = levelIndex;
    }

    public BigDecimal getLowerPrice() {
        return lowerPrice;
    }

    public void setLowerPrice(BigDecimal lowerPrice) {
        this.lowerPrice = lowerPrice;
    }

    public BigDecimal getUpperPrice() {
        return upperPrice;
    }

    public void setUpperPrice(BigDecimal upperPrice) {
        this.upperPrice = upperPrice;
    }

    public BigDecimal getWeightPercent() {
        return weightPercent;
    }

    public void setWeightPercent(BigDecimal weightPercent) {
        this.weightPercent = weightPercent;
    }

    public BigDecimal getBtcAllocated() {
        return btcAllocated;
    }

    public void setBtcAllocated(BigDecimal btcAllocated) {
        this.btcAllocated = btcAllocated;
    }

    public boolean isHolding() {
        return holding;
    }

    public void setHolding(boolean holding) {
        this.holding = holding;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getBtcBought() {
        return btcBought;
    }

    public void setBtcBought(BigDecimal btcBought) {
        this.btcBought = btcBought;
    }

    public BigDecimal getUsdtSpent() {
        return usdtSpent;
    }

    public void setUsdtSpent(BigDecimal usdtSpent) {
        this.usdtSpent = usdtSpent;
    }

    public BigDecimal getBtcSold() {
        return btcSold;
    }

    public void setBtcSold(BigDecimal btcSold) {
        this.btcSold = btcSold;
    }

    public BigDecimal getUsdtReceived() {
        return usdtReceived;
    }

    public void setUsdtReceived(BigDecimal usdtReceived) {
        this.usdtReceived = usdtReceived;
    }

    public BigDecimal getPnlUsdt() {
        return pnlUsdt;
    }

    public void setPnlUsdt(BigDecimal pnlUsdt) {
        this.pnlUsdt = pnlUsdt;
    }

    public BigDecimal getPnlBtc() {
        return pnlBtc;
    }

    public void setPnlBtc(BigDecimal pnlBtc) {
        this.pnlBtc = pnlBtc;
    }

    public int getTradeCount() {
        return tradeCount;
    }

    public void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }

    public int getCycleSells() {
        return cycleSells;
    }

    public void setCycleSells(int cycleSells) {
        this.cycleSells = cycleSells;
    }
}
