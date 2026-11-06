package com.traiding.app.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Conjunto de niveles ("grilla") definido por el usuario sobre un par spot.
 *
 * <p>{@code floorPrice} es el piso, {@code ceilPrice} el techo y {@code levelCount} el número
 * de niveles. Cada nivel recibe un porcentaje del {@code btcInventory} (la suma debe ser 100%).
 * {@code usdtReserve} es el efectivo disponible para ejecutar las compras.</p>
 */
@Table("grid_set")
public class GridSet {

    @Id
    @Column("id")
    private Long id;

    @Column("symbol")
    private String symbol;

    @Column("floor_price")
    private BigDecimal floorPrice;

    @Column("ceil_price")
    private BigDecimal ceilPrice;

    @Column("level_count")
    private Integer levelCount;

    @Column("btc_inventory")
    private BigDecimal btcInventory;

    @Column("usdt_reserve")
    private BigDecimal usdtReserve;

    @Column("fee_rate")
    private BigDecimal feeRate;

    @Column("status")
    private String status;

    @Column("cycle_sells")
    private int cycleSells;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("exhausted_at")
    private LocalDateTime exhaustedAt;

    @Column("origin_set_id")
    private Long originSetId;

    public GridSet() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getFloorPrice() {
        return floorPrice;
    }

    public void setFloorPrice(BigDecimal floorPrice) {
        this.floorPrice = floorPrice;
    }

    public BigDecimal getCeilPrice() {
        return ceilPrice;
    }

    public void setCeilPrice(BigDecimal ceilPrice) {
        this.ceilPrice = ceilPrice;
    }

    public Integer getLevelCount() {
        return levelCount;
    }

    public void setLevelCount(Integer levelCount) {
        this.levelCount = levelCount;
    }

    public BigDecimal getBtcInventory() {
        return btcInventory;
    }

    public void setBtcInventory(BigDecimal btcInventory) {
        this.btcInventory = btcInventory;
    }

    public BigDecimal getUsdtReserve() {
        return usdtReserve;
    }

    public void setUsdtReserve(BigDecimal usdtReserve) {
        this.usdtReserve = usdtReserve;
    }

    public BigDecimal getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(BigDecimal feeRate) {
        this.feeRate = feeRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getCycleSells() {
        return cycleSells;
    }

    public void setCycleSells(int cycleSells) {
        this.cycleSells = cycleSells;
    }

    public LocalDateTime getExhaustedAt() {
        return exhaustedAt;
    }

    public void setExhaustedAt(LocalDateTime exhaustedAt) {
        this.exhaustedAt = exhaustedAt;
    }

    public Long getOriginSetId() {
        return originSetId;
    }

    public void setOriginSetId(Long originSetId) {
        this.originSetId = originSetId;
    }

    /** Estado tipado derivado del texto persistido. */
    public GridSetStatus statusEnum() {
        return GridSetStatus.from(status);
    }
}
