package com.traiding.app.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("grid_alert")
public class GridAlert {

    @Id
    @Column("id")
    private Long id;

    @Column("grid_set_id")
    private Long gridSetId;

    @Column("grid_level_id")
    private Long gridLevelId;

    @Column("level_index")
    private Integer levelIndex;

    @Column("type")
    private String type;

    @Column("status")
    private String status;

    @Column("trigger_price")
    private BigDecimal triggerPrice;

    @Column("suggested_quantity")
    private BigDecimal suggestedQuantity;

    @Column("suggested_price")
    private BigDecimal suggestedPrice;

    @Column("resolved_at")
    private LocalDateTime resolvedAt;

    @Column("trade_id")
    private Long tradeId;

    @Column("note")
    private String note;

    @Column("created_at")
    private LocalDateTime createdAt;

    public GridAlert() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public BigDecimal getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public void setSuggestedQuantity(BigDecimal suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }

    public BigDecimal getSuggestedPrice() {
        return suggestedPrice;
    }

    public void setSuggestedPrice(BigDecimal suggestedPrice) {
        this.suggestedPrice = suggestedPrice;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public void setTradeId(Long tradeId) {
        this.tradeId = tradeId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
