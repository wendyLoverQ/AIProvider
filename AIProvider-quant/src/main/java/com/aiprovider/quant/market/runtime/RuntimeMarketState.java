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
    private final RuntimeMarkPrice latestMarkPrice;
    private final Instant lastKlineEventTime;
    private final Instant lastBookTickerEventTime;
    private final Instant lastMarkPriceEventTime;
    private final String lastKlineEventFingerprint;
    private final String lastBookTickerEventFingerprint;
    private final String lastMarkPriceEventFingerprint;

    RuntimeMarketState(RuntimeMarketKey key, int maxClosedCandles,
                       List<RuntimeClosedCandle> closedCandles,
                       RuntimeTopOfBook latestTopOfBook,
                       RuntimeMarkPrice latestMarkPrice,
                       Instant lastKlineEventTime,
                       Instant lastBookTickerEventTime,
                       Instant lastMarkPriceEventTime,
                       String lastKlineEventFingerprint,
                       String lastBookTickerEventFingerprint,
                       String lastMarkPriceEventFingerprint) {
        if (key == null || maxClosedCandles < 2 || closedCandles == null
                || closedCandles.size() > maxClosedCandles
                || (lastKlineEventTime == null) != (lastKlineEventFingerprint == null)
                || (lastBookTickerEventTime == null) != (lastBookTickerEventFingerprint == null)
                || (lastMarkPriceEventTime == null) != (lastMarkPriceEventFingerprint == null)
                || !validMarkPrice(key, latestMarkPrice, lastMarkPriceEventTime)) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.STATE_INVALID,
                    "runtime market state fields are inconsistent");
        }
        this.key = key;
        this.maxClosedCandles = maxClosedCandles;
        this.closedCandles = Collections.unmodifiableList(new ArrayList<>(closedCandles));
        this.latestTopOfBook = latestTopOfBook;
        this.latestMarkPrice = latestMarkPrice;
        this.lastKlineEventTime = RuntimeClosedCandle.copy(lastKlineEventTime);
        this.lastBookTickerEventTime = RuntimeClosedCandle.copy(lastBookTickerEventTime);
        this.lastMarkPriceEventTime = RuntimeClosedCandle.copy(lastMarkPriceEventTime);
        this.lastKlineEventFingerprint = lastKlineEventFingerprint;
        this.lastBookTickerEventFingerprint = lastBookTickerEventFingerprint;
        this.lastMarkPriceEventFingerprint = lastMarkPriceEventFingerprint;
    }

    private static boolean validMarkPrice(
            RuntimeMarketKey key, RuntimeMarkPrice markPrice, Instant watermark) {
        if (markPrice == null) {
            return watermark == null;
        }
        return watermark != null
                && markPrice.getProvider() == key.getProvider()
                && markPrice.getMarketType() == key.getMarketType()
                && markPrice.getSymbol().equals(key.getSymbol())
                && markPrice.getEventTime().equals(watermark);
    }

    public RuntimeMarketKey getKey() { return key; }
    public int getMaxClosedCandles() { return maxClosedCandles; }
    public List<RuntimeClosedCandle> getClosedCandles() { return closedCandles; }
    public RuntimeTopOfBook getLatestTopOfBook() { return latestTopOfBook; }
    public RuntimeMarkPrice getLatestMarkPrice() { return latestMarkPrice; }
    public Instant getLastKlineEventTime() { return RuntimeClosedCandle.copy(lastKlineEventTime); }
    public Instant getLastBookTickerEventTime() { return RuntimeClosedCandle.copy(lastBookTickerEventTime); }
    public Instant getLastMarkPriceEventTime() {
        return RuntimeClosedCandle.copy(lastMarkPriceEventTime);
    }
    public String getLastKlineEventFingerprint() { return lastKlineEventFingerprint; }
    public String getLastBookTickerEventFingerprint() { return lastBookTickerEventFingerprint; }
    public String getLastMarkPriceEventFingerprint() { return lastMarkPriceEventFingerprint; }

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
