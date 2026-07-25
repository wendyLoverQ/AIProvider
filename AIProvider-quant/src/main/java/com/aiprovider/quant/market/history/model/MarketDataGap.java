package com.aiprovider.quant.market.history.model;

import java.time.Instant;

/**
 * 数据集缺口记录。
 *
 * 对应 {@code q_market_data_gap} 表。表示在数据集已保存区间内
 * （earliestOpenTime ～ latestOpenTime）缺失的一段 K 线。
 */
public class MarketDataGap {

    private long id;
    private long datasetId;
    private Instant startOpenTime;
    private Instant endOpenTimeExclusive;
    private long missingCount;
    private String detectedByTaskId;
    private Instant detectedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDatasetId() { return datasetId; }
    public void setDatasetId(long datasetId) { this.datasetId = datasetId; }

    public Instant getStartOpenTime() { return startOpenTime; }
    public void setStartOpenTime(Instant startOpenTime) { this.startOpenTime = startOpenTime; }

    public Instant getEndOpenTimeExclusive() { return endOpenTimeExclusive; }
    public void setEndOpenTimeExclusive(Instant endOpenTimeExclusive) { this.endOpenTimeExclusive = endOpenTimeExclusive; }

    public long getMissingCount() { return missingCount; }
    public void setMissingCount(long missingCount) { this.missingCount = missingCount; }

    public String getDetectedByTaskId() { return detectedByTaskId; }
    public void setDetectedByTaskId(String detectedByTaskId) { this.detectedByTaskId = detectedByTaskId; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }
}
