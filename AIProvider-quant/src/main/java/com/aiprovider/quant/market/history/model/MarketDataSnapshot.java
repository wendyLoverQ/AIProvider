package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;
import java.util.List;

/** Immutable, validated database input for a future backtest. */
public final class MarketDataSnapshot {
    private final long datasetId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final MarketDataType dataType;
    private final String symbol;
    private final KlineInterval interval;
    private final Instant startOpenTimeInclusive;
    private final Instant endOpenTimeExclusive;
    private final long expectedCandleCount;
    private final long actualCandleCount;
    private final Instant datasetLastValidatedAt;
    private final String datasetLastSyncTaskId;
    private final List<HistoricalCandle> candles;

    public MarketDataSnapshot(long datasetId, MarketProviderId provider, MarketType marketType,
                              MarketDataType dataType, String symbol, KlineInterval interval,
                              Instant startOpenTimeInclusive, Instant endOpenTimeExclusive,
                              long expectedCandleCount, long actualCandleCount,
                              Instant datasetLastValidatedAt, String datasetLastSyncTaskId,
                              List<HistoricalCandle> candles) {
        this.datasetId = datasetId;
        this.provider = provider;
        this.marketType = marketType;
        this.dataType = dataType;
        this.symbol = symbol;
        this.interval = interval;
        this.startOpenTimeInclusive = startOpenTimeInclusive;
        this.endOpenTimeExclusive = endOpenTimeExclusive;
        this.expectedCandleCount = expectedCandleCount;
        this.actualCandleCount = actualCandleCount;
        this.datasetLastValidatedAt = datasetLastValidatedAt;
        this.datasetLastSyncTaskId = datasetLastSyncTaskId;
        this.candles = List.copyOf(candles);
    }

    public long getDatasetId() { return datasetId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public MarketDataType getDataType() { return dataType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public Instant getStartOpenTimeInclusive() { return startOpenTimeInclusive; }
    public Instant getEndOpenTimeExclusive() { return endOpenTimeExclusive; }
    public long getExpectedCandleCount() { return expectedCandleCount; }
    public long getActualCandleCount() { return actualCandleCount; }
    public Instant getDatasetLastValidatedAt() { return datasetLastValidatedAt; }
    public String getDatasetLastSyncTaskId() { return datasetLastSyncTaskId; }
    public List<HistoricalCandle> getCandles() { return candles; }
}
