package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;

/**
 * 同步任务创建结果（返回给调用方）。
 */
public class MarketSyncProgress {

    private String taskId;
    private long datasetId;
    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private KlineInterval interval;
    private Instant normalizedStartTime;
    private Instant normalizedEndTime;
    private long expectedCount;
    private MarketSyncTaskStatus status;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public long getDatasetId() { return datasetId; }
    public void setDatasetId(long datasetId) { this.datasetId = datasetId; }

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public Instant getNormalizedStartTime() { return normalizedStartTime; }
    public void setNormalizedStartTime(Instant normalizedStartTime) { this.normalizedStartTime = normalizedStartTime; }

    public Instant getNormalizedEndTime() { return normalizedEndTime; }
    public void setNormalizedEndTime(Instant normalizedEndTime) { this.normalizedEndTime = normalizedEndTime; }

    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    public MarketSyncTaskStatus getStatus() { return status; }
    public void setStatus(MarketSyncTaskStatus status) { this.status = status; }
}
