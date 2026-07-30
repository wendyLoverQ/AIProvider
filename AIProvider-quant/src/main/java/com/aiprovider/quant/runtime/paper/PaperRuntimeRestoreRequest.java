package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.market.runtime.RuntimeMarketState;

import java.time.Instant;
import java.util.Objects;

/** Immutable raw persisted fields used to restore one paper runtime. */
public final class PaperRuntimeRestoreRequest {
    private final PaperRuntimeConfig config;
    private final RuntimeMarketState marketState;
    private final PaperTradingSessionSnapshot tradingSession;
    private final Instant lastProcessedEventTime;
    private final PaperRuntimeStepType lastStepType;

    public PaperRuntimeRestoreRequest(PaperRuntimeConfig config, RuntimeMarketState marketState,
                                      PaperTradingSessionSnapshot tradingSession,
                                      Instant lastProcessedEventTime,
                                      PaperRuntimeStepType lastStepType) {
        this.config = config;
        this.marketState = marketState;
        this.tradingSession = tradingSession;
        this.lastProcessedEventTime = lastProcessedEventTime;
        this.lastStepType = lastStepType;
    }

    public PaperRuntimeConfig getConfig() { return config; }
    public RuntimeMarketState getMarketState() { return marketState; }
    public PaperTradingSessionSnapshot getTradingSession() { return tradingSession; }
    public Instant getLastProcessedEventTime() { return lastProcessedEventTime; }
    public PaperRuntimeStepType getLastStepType() { return lastStepType; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperRuntimeRestoreRequest that)) return false;
        return Objects.equals(config, that.config) && Objects.equals(marketState, that.marketState)
                && Objects.equals(tradingSession, that.tradingSession)
                && Objects.equals(lastProcessedEventTime, that.lastProcessedEventTime)
                && lastStepType == that.lastStepType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, marketState, tradingSession, lastProcessedEventTime, lastStepType);
    }
}
