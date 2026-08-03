package com.delfino.expensetracker.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_user_id", columnList = "user_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 5000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expense_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> expenseIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "chart_definitions", nullable = false, columnDefinition = "jsonb")
    private JsonNode chartDefinitions;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_by", nullable = false, length = 50)
    private ReportGroupBy groupBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_snapshot", columnDefinition = "jsonb")
    private JsonNode filterSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode insights;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getExpenseIds() {
        return expenseIds;
    }

    public void setExpenseIds(List<Long> expenseIds) {
        this.expenseIds = expenseIds;
    }

    public JsonNode getChartDefinitions() {
        return chartDefinitions;
    }

    public void setChartDefinitions(JsonNode chartDefinitions) {
        this.chartDefinitions = chartDefinitions;
    }

    public ReportGroupBy getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(ReportGroupBy groupBy) {
        this.groupBy = groupBy;
    }

    public JsonNode getFilterSnapshot() {
        return filterSnapshot;
    }

    public void setFilterSnapshot(JsonNode filterSnapshot) {
        this.filterSnapshot = filterSnapshot;
    }

    public JsonNode getInsights() {
        return insights;
    }

    public void setInsights(JsonNode insights) {
        this.insights = insights;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

