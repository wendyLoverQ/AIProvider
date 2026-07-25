package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;

/**
 * 历史行情数据集。
 *
 * 对应 {@code q_market_dataset} 表。一个数据集由
 * (provider, marketType, dataType, symbol, interval) 唯一标识，
 * 记录该组合下已保存 K 线的覆盖范围、数量、缺口和状态。
 */
public class MarketDataset {

    private long id;
    private MarketProviderId provider;
    private MarketType marketType;
    private MarketDataType dataType;
    private String symbol;
    private KlineInterval interval;
    private Instant earliestOpenTime;
    private Instant latestOpenTime;
    private long candleCount;
    private long expectedInsideRange;
    private long gapCount;
    private int gapSegmentCount;
    private MarketDatasetStatus status;
    private Instant lastSuccessfulSyncAt;
    private Instant lastValidatedAt;
    private String lastSyncTaskId;
    private Instant createdAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public MarketDataType getDataType() { return dataType; }
    public void setDataType(MarketDataType dataType) { this.dataType = dataType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public Instant getEarliestOpenTime() { return earliestOpenTime; }
    public void setEarliestOpenTime(Instant earliestOpenTime) { this.earliestOpenTime = earliestOpenTime; }

    public Instant getLatestOpenTime() { return latestOpenTime; }
    public void setLatestOpenTime(Instant latestOpenTime) { this.latestOpenTime = latestOpenTime; }

    public long getCandleCount() { return candleCount; }
    public void setCandleCount(long candleCount) { this.candleCount = candleCount; }

    public long getExpectedInsideRange() { return expectedInsideRange; }
    public void setExpectedInsideRange(long expectedInsideRange) { this.expectedInsideRange = expectedInsideRange; }

    public long getGapCount() { return gapCount; }
    public void setGapCount(long gapCount) { this.gapCount = gapCount; }

    public int getGapSegmentCount() { return gapSegmentCount; }
    public void setGapSegmentCount(int gapSegmentCount) { this.gapSegmentCount = gapSegmentCount; }

    public MarketDatasetStatus getStatus() { return status; }
    public void setStatus(MarketDatasetStatus status) { this.status = status; }

    public Instant getLastSuccessfulSyncAt() { return lastSuccessfulSyncAt; }
    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) { this.lastSuccessfulSyncAt = lastSuccessfulSyncAt; }

    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public void setLastValidatedAt(Instant lastValidatedAt) { this.lastValidatedAt = lastValidatedAt; }

    public String getLastSyncTaskId() { return lastSyncTaskId; }
    public void setLastSyncTaskId(String lastSyncTaskId) { this.lastSyncTaskId = lastSyncTaskId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 构建数据集唯一键字符串，用于活动任务锁。
     *
     * @return provider:marketType:dataType:symbol:intervalCode
     */
    public String activeDatasetKey() {
        return provider.name() + ":" + marketType.name() + ":" + dataType.name()
                + ":" + symbol + ":" + interval.code();
    }
}
