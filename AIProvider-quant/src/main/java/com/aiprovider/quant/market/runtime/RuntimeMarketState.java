package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable market state for exactly one runtime market key. */
public final class RuntimeMarketState {
    private final RuntimeMarketKey key;
    private final int maxClosedCandles;
    private final List<RuntimeClosedCandle> closedCandles;
    private final RuntimeTopOfBook latestTopOfBook;
    private final Instant lastKlineEventTime;
    private final Instant lastBookTickerEventTime;
    private final String lastKlineEventFingerprint;
    private final String lastBookTickerEventFingerprint;

    RuntimeMarketState(RuntimeMarketKey key, int maxClosedCandles,
                       List<RuntimeClosedCandle> closedCandles,
                       RuntimeTopOfBook latestTopOfBook,
                       Instant lastKlineEventTime,
                       Instant lastBookTickerEventTime,
                       String lastKlineEventFingerprint,
                       String lastBookTickerEventFingerprint) {
        if (key == null || maxClosedCandles < 2 || closedCandles == null
                || closedCandles.size() > maxClosedCandles
                || (lastKlineEventTime == null) != (lastKlineEventFingerprint == null)
                || (lastBookTickerEventTime == null) != (lastBookTickerEventFingerprint == null)) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.STATE_INVALID,
                    "runtime market state fields are inconsistent");
        }
        this.key = key;
        this.maxClosedCandles = maxClosedCandles;
        this.closedCandles = Collections.unmodifiableList(new ArrayList<>(closedCandles));
        this.latestTopOfBook = latestTopOfBook;
        this.lastKlineEventTime = RuntimeClosedCandle.copy(lastKlineEventTime);
        this.lastBookTickerEventTime = RuntimeClosedCandle.copy(lastBookTickerEventTime);
        this.lastKlineEventFingerprint = lastKlineEventFingerprint;
        this.lastBookTickerEventFingerprint = lastBookTickerEventFingerprint;
    }

    public RuntimeMarketKey getKey() { return key; }
    public int getMaxClosedCandles() { return maxClosedCandles; }
    public List<RuntimeClosedCandle> getClosedCandles() { return closedCandles; }
    public RuntimeTopOfBook getLatestTopOfBook() { return latestTopOfBook; }
    public Instant getLastKlineEventTime() { return RuntimeClosedCandle.copy(lastKlineEventTime); }
    public Instant getLastBookTickerEventTime() { return RuntimeClosedCandle.copy(lastBookTickerEventTime); }
    public String getLastKlineEventFingerprint() { return lastKlineEventFingerprint; }
    public String getLastBookTickerEventFingerprint() { return lastBookTickerEventFingerprint; }

    public List<HistoricalCandle> toHistoricalCandles() {
        List<HistoricalCandle> result = new ArrayList<>(closedCandles.size());
        for (RuntimeClosedCandle candle : closedCandles) {
            result.add(candle.toHistoricalCandle());
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeMarketState)) return false;
        RuntimeMarketState that = (RuntimeMarketState) other;
        return maxClosedCandles == that.maxClosedCandles && key.equals(that.key)
                && closedCandles.equals(that.closedCandles)
                && Objects.equals(latestTopOfBook, that.latestTopOfBook)
                && Objects.equals(lastKlineEventTime, that.lastKlineEventTime)
                && Objects.equals(lastBookTickerEventTime, that.lastBookTickerEventTime)
                && Objects.equals(lastKlineEventFingerprint, that.lastKlineEventFingerprint)
                && Objects.equals(lastBookTickerEventFingerprint, that.lastBookTickerEventFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, maxClosedCandles, closedCandles, latestTopOfBook,
                lastKlineEventTime, lastBookTickerEventTime,
                lastKlineEventFingerprint, lastBookTickerEventFingerprint);
    }
}
