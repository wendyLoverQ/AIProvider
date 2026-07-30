package com.aiprovider.quant.market.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable persisted fields used to rehydrate one runtime market state. */
public final class RuntimeMarketStateRestoreRequest {
    private final RuntimeMarketKey key;
    private final int maxClosedCandles;
    private final List<RuntimeClosedCandle> closedCandles;
    private final RuntimeTopOfBook latestTopOfBook;
    private final RuntimeMarkPrice latestMarkPrice;
    private final Instant lastKlineEventTime;
    private final Instant lastBookTickerEventTime;
    private final Instant lastMarkPriceEventTime;
    private final String lastKlineEventFingerprint;
    private final String lastBookTickerEventFingerprint;
    private final String lastMarkPriceEventFingerprint;

    public RuntimeMarketStateRestoreRequest(
            RuntimeMarketKey key,
            int maxClosedCandles,
            List<RuntimeClosedCandle> closedCandles,
            RuntimeTopOfBook latestTopOfBook,
            RuntimeMarkPrice latestMarkPrice,
            Instant lastKlineEventTime,
            Instant lastBookTickerEventTime,
            Instant lastMarkPriceEventTime,
            String lastKlineEventFingerprint,
            String lastBookTickerEventFingerprint,
            String lastMarkPriceEventFingerprint) {
        this.key = key;
        this.maxClosedCandles = maxClosedCandles;
        this.closedCandles = immutableCopy(closedCandles);
        this.latestTopOfBook = latestTopOfBook;
        this.latestMarkPrice = latestMarkPrice;
        this.lastKlineEventTime = lastKlineEventTime;
        this.lastBookTickerEventTime = lastBookTickerEventTime;
        this.lastMarkPriceEventTime = lastMarkPriceEventTime;
        this.lastKlineEventFingerprint = lastKlineEventFingerprint;
        this.lastBookTickerEventFingerprint = lastBookTickerEventFingerprint;
        this.lastMarkPriceEventFingerprint = lastMarkPriceEventFingerprint;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null) return null;
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public RuntimeMarketKey getKey() { return key; }
    public int getMaxClosedCandles() { return maxClosedCandles; }
    public List<RuntimeClosedCandle> getClosedCandles() { return closedCandles; }
    public RuntimeTopOfBook getLatestTopOfBook() { return latestTopOfBook; }
    public RuntimeMarkPrice getLatestMarkPrice() { return latestMarkPrice; }
    public Instant getLastKlineEventTime() { return lastKlineEventTime; }
    public Instant getLastBookTickerEventTime() { return lastBookTickerEventTime; }
    public Instant getLastMarkPriceEventTime() { return lastMarkPriceEventTime; }
    public String getLastKlineEventFingerprint() { return lastKlineEventFingerprint; }
    public String getLastBookTickerEventFingerprint() { return lastBookTickerEventFingerprint; }
    public String getLastMarkPriceEventFingerprint() { return lastMarkPriceEventFingerprint; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeMarketStateRestoreRequest that)) return false;
        return maxClosedCandles == that.maxClosedCandles
                && Objects.equals(key, that.key)
                && Objects.equals(closedCandles, that.closedCandles)
                && Objects.equals(latestTopOfBook, that.latestTopOfBook)
                && Objects.equals(latestMarkPrice, that.latestMarkPrice)
                && Objects.equals(lastKlineEventTime, that.lastKlineEventTime)
                && Objects.equals(lastBookTickerEventTime, that.lastBookTickerEventTime)
                && Objects.equals(lastMarkPriceEventTime, that.lastMarkPriceEventTime)
                && Objects.equals(lastKlineEventFingerprint, that.lastKlineEventFingerprint)
                && Objects.equals(lastBookTickerEventFingerprint, that.lastBookTickerEventFingerprint)
                && Objects.equals(lastMarkPriceEventFingerprint, that.lastMarkPriceEventFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, maxClosedCandles, closedCandles, latestTopOfBook, latestMarkPrice,
                lastKlineEventTime, lastBookTickerEventTime, lastMarkPriceEventTime,
                lastKlineEventFingerprint, lastBookTickerEventFingerprint,
                lastMarkPriceEventFingerprint);
    }
}
