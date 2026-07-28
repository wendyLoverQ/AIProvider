package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.portfolio.sizing.PositionSizingResult;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;

import java.time.Instant;
import java.util.Objects;

public final class PaperTradingSessionSnapshot {
    private final PaperTradingSessionConfig config;
    private final PaperAccountSnapshot paperAccountSnapshot;
    private final ExecutionOrderSnapshot pendingOrderSnapshot;
    private final ExecutionOrderSnapshot lastOrderSnapshot;
    private final StrategySignalDecision lastSignalDecision;
    private final PositionSizingResult lastSizingResult;
    private final PreTradeRiskDecision lastRiskDecision;
    private final PaperSignalCandleSnapshot lastEvaluatedCandle;
    private final Instant lastUpdatedAt;

    public PaperTradingSessionSnapshot(
            PaperTradingSessionConfig config,
            PaperAccountSnapshot paperAccountSnapshot,
            ExecutionOrderSnapshot pendingOrderSnapshot,
            ExecutionOrderSnapshot lastOrderSnapshot,
            StrategySignalDecision lastSignalDecision,
            PositionSizingResult lastSizingResult,
            PreTradeRiskDecision lastRiskDecision,
            PaperSignalCandleSnapshot lastEvaluatedCandle,
            Instant lastUpdatedAt) {
        if (config == null || paperAccountSnapshot == null || lastUpdatedAt == null) {
            throw new PaperTradingException(PaperTradingException.PAPER_TRADING_STATE_INVALID,
                    "config, paperAccountSnapshot and lastUpdatedAt are required");
        }
        if (!lastUpdatedAt.equals(paperAccountSnapshot.getLastUpdatedAt())) {
            throw new PaperTradingException(PaperTradingException.PAPER_TRADING_STATE_INVALID,
                    "session lastUpdatedAt must equal account lastUpdatedAt");
        }
        if (pendingOrderSnapshot != null
                && pendingOrderSnapshot.getStatus() != ExecutionOrderStatus.SUBMITTED
                && pendingOrderSnapshot.getStatus() != ExecutionOrderStatus.PARTIALLY_FILLED) {
            throw new PaperTradingException(PaperTradingException.PAPER_TRADING_STATE_INVALID,
                    "pending order must be SUBMITTED or PARTIALLY_FILLED");
        }
        this.config = config;
        this.paperAccountSnapshot = paperAccountSnapshot;
        this.pendingOrderSnapshot = pendingOrderSnapshot;
        this.lastOrderSnapshot = lastOrderSnapshot;
        this.lastSignalDecision = lastSignalDecision;
        this.lastSizingResult = lastSizingResult;
        this.lastRiskDecision = lastRiskDecision;
        this.lastEvaluatedCandle = lastEvaluatedCandle;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public PaperTradingSessionConfig getConfig() { return config; }
    public PaperAccountSnapshot getPaperAccountSnapshot() { return paperAccountSnapshot; }
    public ExecutionOrderSnapshot getPendingOrderSnapshot() { return pendingOrderSnapshot; }
    public ExecutionOrderSnapshot getLastOrderSnapshot() { return lastOrderSnapshot; }
    public StrategySignalDecision getLastSignalDecision() { return lastSignalDecision; }
    public PositionSizingResult getLastSizingResult() { return lastSizingResult; }
    public PreTradeRiskDecision getLastRiskDecision() { return lastRiskDecision; }
    public PaperSignalCandleSnapshot getLastEvaluatedCandle() { return lastEvaluatedCandle; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperTradingSessionSnapshot that)) return false;
        return Objects.equals(config, that.config)
                && Objects.equals(paperAccountSnapshot, that.paperAccountSnapshot)
                && Objects.equals(pendingOrderSnapshot, that.pendingOrderSnapshot)
                && Objects.equals(lastOrderSnapshot, that.lastOrderSnapshot)
                && Objects.equals(lastSignalDecision, that.lastSignalDecision)
                && Objects.equals(lastSizingResult, that.lastSizingResult)
                && Objects.equals(lastRiskDecision, that.lastRiskDecision)
                && Objects.equals(lastEvaluatedCandle, that.lastEvaluatedCandle)
                && Objects.equals(lastUpdatedAt, that.lastUpdatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, paperAccountSnapshot, pendingOrderSnapshot, lastOrderSnapshot,
                lastSignalDecision, lastSizingResult, lastRiskDecision, lastEvaluatedCandle, lastUpdatedAt);
    }
}
