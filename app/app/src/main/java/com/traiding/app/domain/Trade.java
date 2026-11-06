package com.traiding.app.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Operación ejecutada (compra o venta) dentro de un nivel de la grilla. */
@Table("trade_history")
public class Trade {

    @Id
    @Column("id")
    private Long id;

    @Column("grid_set_id")
    private Long gridSetId;

    @Column("grid_level_id")
    private Long gridLevelId;

    @Column("level_index")
    private Integer levelIndex;

    @Column("side")
    private String side;

    @Column("price")
    private BigDecimal price;

    @Column("quantity")
    private BigDecimal quantity;

    @Column("amount")
    private BigDecimal amount;

    @Column("fee")
    private BigDecimal fee;

    @Column("pnl_usdt")
    private BigDecimal pnlUsdt = BigDecimal.ZERO;

    @Column("pnl_btc")
    private BigDecimal pnlBtc = BigDecimal.ZERO;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Trade() {
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

    public Long getGridLevelId() {
        return gridLevelId;
    }

    public void setGridLevelId(Long gridLevelId) {
        this.gridLevelId = gridLevelId;
    }

    public Integer getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(Integer levelIndex) {
        this.levelIndex = levelIndex;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
