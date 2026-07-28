package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.market.runtime.RuntimeMarketState;

import java.time.Instant;
import java.util.Objects;

/** Immutable combined market and paper-trading runtime state. */
public final class PaperRuntimeSnapshot {
    private final PaperRuntimeConfig config;
    private final RuntimeMarketState marketState;
    private final PaperTradingSessionSnapshot tradingSession;
    private final Instant lastProcessedEventTime;
    private final PaperRuntimeStepType lastStepType;

    public PaperRuntimeSnapshot(PaperRuntimeConfig config, RuntimeMarketState marketState,
                                PaperTradingSessionSnapshot tradingSession,
                                Instant lastProcessedEventTime,
                                PaperRuntimeStepType lastStepType) {
        if (config == null || marketState == null || tradingSession == null
                || (lastProcessedEventTime == null) != (lastStepType == null)) {
            throw invalid("config, marketState and tradingSession are required; last event fields must coexist");
        }
        if (!config.getMarketKey().equals(marketState.getKey())
                || config.getMaxClosedCandles() != marketState.getMaxClosedCandles()) {
            throw mismatch("marketState does not match runtime config");
        }
        PaperTradingSessionConfig tradingConfig = tradingSession.getConfig();
        if (!config.getTradingConfig().equals(tradingConfig)) {
            throw mismatch("trading session config does not match runtime config");
        }
        PaperAccountSnapshot account = tradingSession.getPaperAccountSnapshot();
        if (account.getProvider() != tradingConfig.getProvider()
                || account.getMarketType() != tradingConfig.getMarketType()
                || !account.getQuoteAsset().equals(tradingConfig.getSimulatedExecutionPolicy().getFeeAsset())
                || (account.getPosition().isOpen()
                && !tradingConfig.getSymbol().equals(account.getPosition().getSymbol()))) {
            throw mismatch("trading account context does not match runtime config");
        }
        this.config = config;
        this.marketState = marketState;
        this.tradingSession = tradingSession;
        this.lastProcessedEventTime = copy(lastProcessedEventTime);
        this.lastStepType = lastStepType;
    }

    public PaperRuntimeConfig getConfig() { return config; }
    public RuntimeMarketState getMarketState() { return marketState; }
    public RuntimeMarketState getRuntimeMarketState() { return marketState; }
    public PaperTradingSessionSnapshot getTradingSession() { return tradingSession; }
    public PaperTradingSessionSnapshot getPaperTradingSessionSnapshot() { return tradingSession; }
    public Instant getLastProcessedEventTime() { return copy(lastProcessedEventTime); }
    public PaperRuntimeStepType getLastStepType() { return lastStepType; }

    private static Instant copy(Instant value) {
        return value == null ? null : Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    private static PaperRuntimeException invalid(String message) {
        return new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_STATE_INVALID, message);
    }

    private static PaperRuntimeException mismatch(String message) {
        return new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_CONTEXT_MISMATCH, message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperRuntimeSnapshot that)) return false;
        return config.equals(that.config) && marketState.equals(that.marketState)
                && tradingSession.equals(that.tradingSession)
                && Objects.equals(lastProcessedEventTime, that.lastProcessedEventTime)
                && lastStepType == that.lastStepType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, marketState, tradingSession, lastProcessedEventTime, lastStepType);
    }
}
