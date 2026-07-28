package com.aiprovider.quant.market.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Deterministic result of one accepted runtime market event. */
public final class RuntimeMarketUpdateResult {
    private final RuntimeMarketUpdateType updateType;
    private final RuntimeMarketState state;
    private final RuntimeClosedCandle addedClosedCandle;
    private final RuntimeTopOfBook latestTopOfBook;
    private final int closedCandleCount;
    private final Instant windowStartTime;
    private final Instant windowEndTime;

    RuntimeMarketUpdateResult(RuntimeMarketUpdateType updateType, RuntimeMarketState state,
                              RuntimeClosedCandle addedClosedCandle) {
        if (updateType == null || state == null) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.STATE_INVALID,
                    "update type and state are required");
        }
        this.updateType = updateType;
        this.state = state;
        this.addedClosedCandle = addedClosedCandle;
        this.latestTopOfBook = state.getLatestTopOfBook();
        List<RuntimeClosedCandle> candles = state.getClosedCandles();
        this.closedCandleCount = candles.size();
        this.windowStartTime = candles.isEmpty() ? null : candles.get(0).getOpenTime();
        this.windowEndTime = candles.isEmpty() ? null : candles.get(candles.size() - 1).getCloseTime();
    }

    public RuntimeMarketUpdateType getUpdateType() { return updateType; }
    public RuntimeMarketState getState() { return state; }
    public RuntimeClosedCandle getAddedClosedCandle() { return addedClosedCandle; }
    public RuntimeTopOfBook getLatestTopOfBook() { return latestTopOfBook; }
    public int getClosedCandleCount() { return closedCandleCount; }
    public Instant getWindowStartTime() { return RuntimeClosedCandle.copy(windowStartTime); }
    public Instant getWindowEndTime() { return RuntimeClosedCandle.copy(windowEndTime); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeMarketUpdateResult)) return false;
        RuntimeMarketUpdateResult that = (RuntimeMarketUpdateResult) other;
        return closedCandleCount == that.closedCandleCount && updateType == that.updateType
                && state.equals(that.state) && Objects.equals(addedClosedCandle, that.addedClosedCandle)
                && Objects.equals(latestTopOfBook, that.latestTopOfBook)
                && Objects.equals(windowStartTime, that.windowStartTime)
                && Objects.equals(windowEndTime, that.windowEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updateType, state, addedClosedCandle, latestTopOfBook,
                closedCandleCount, windowStartTime, windowEndTime);
    }
}
