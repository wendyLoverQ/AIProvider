package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;
import java.util.ArrayList;
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
        this.candles = copyCandles(candles);
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
    public List<HistoricalCandle> getCandles() { return copyCandles(candles); }

    private static List<HistoricalCandle> copyCandles(List<HistoricalCandle> source) {
        List<HistoricalCandle> copies = new ArrayList<>(source.size());
        for (HistoricalCandle sourceCandle : source) {
            if (sourceCandle == null) {
                copies.add(null);
                continue;
            }
            HistoricalCandle copy = new HistoricalCandle();
            copy.setId(sourceCandle.getId());
            copy.setDatasetId(sourceCandle.getDatasetId());
            copy.setProvider(sourceCandle.getProvider());
            copy.setMarketType(sourceCandle.getMarketType());
            copy.setSymbol(sourceCandle.getSymbol());
            copy.setInterval(sourceCandle.getInterval());
            copy.setOpenTime(sourceCandle.getOpenTime());
            copy.setCloseTime(sourceCandle.getCloseTime());
            copy.setOpenPrice(sourceCandle.getOpenPrice());
            copy.setHighPrice(sourceCandle.getHighPrice());
            copy.setLowPrice(sourceCandle.getLowPrice());
            copy.setClosePrice(sourceCandle.getClosePrice());
            copy.setVolume(sourceCandle.getVolume());
            copy.setQuoteVolume(sourceCandle.getQuoteVolume());
            copy.setTradeCount(sourceCandle.getTradeCount());
            copy.setTakerBuyBaseVolume(sourceCandle.getTakerBuyBaseVolume());
            copy.setTakerBuyQuoteVolume(sourceCandle.getTakerBuyQuoteVolume());
            copy.setSource(sourceCandle.getSource());
            copy.setCreatedAt(sourceCandle.getCreatedAt());
            copies.add(copy);
        }
        return List.copyOf(copies);
    }
}
