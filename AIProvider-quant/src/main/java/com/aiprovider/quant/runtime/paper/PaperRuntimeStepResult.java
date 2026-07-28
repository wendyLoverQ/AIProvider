package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.engine.paper.PaperTradingStepResult;
import com.aiprovider.quant.market.runtime.RuntimeMarketUpdateResult;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable result of one accepted stream event. */
public final class PaperRuntimeStepResult {
    private final PaperRuntimeStepType stepType;
    private final PaperRuntimeSnapshot runtime;
    private final RuntimeMarketUpdateResult marketUpdateResult;
    private final PaperTradingStepResult tradingStepResult;
    private final boolean utcTradingDayRolled;
    private final LocalDate previousUtcDate;
    private final LocalDate currentUtcDate;

    public PaperRuntimeStepResult(PaperRuntimeStepType stepType, PaperRuntimeSnapshot runtime,
                                  RuntimeMarketUpdateResult marketUpdateResult,
                                  PaperTradingStepResult tradingStepResult,
                                  boolean utcTradingDayRolled,
                                  LocalDate previousUtcDate, LocalDate currentUtcDate) {
        if (stepType == null || runtime == null || marketUpdateResult == null
                || previousUtcDate == null || currentUtcDate == null
                || utcTradingDayRolled != currentUtcDate.isAfter(previousUtcDate)
                || (stepType == PaperRuntimeStepType.CLOSED_CANDLE_PROCESSED
                || stepType == PaperRuntimeStepType.PENDING_ORDER_EXECUTED)
                != (tradingStepResult != null)) {
            throw new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_STATE_INVALID,
                    "paper runtime step result fields are inconsistent");
        }
        this.stepType = stepType;
        this.runtime = runtime;
        this.marketUpdateResult = marketUpdateResult;
        this.tradingStepResult = tradingStepResult;
        this.utcTradingDayRolled = utcTradingDayRolled;
        this.previousUtcDate = previousUtcDate;
        this.currentUtcDate = currentUtcDate;
    }

    public PaperRuntimeStepType getStepType() { return stepType; }
    public PaperRuntimeSnapshot getRuntime() { return runtime; }
    public PaperRuntimeSnapshot getRuntimeSnapshot() { return runtime; }
    public RuntimeMarketUpdateResult getMarketUpdateResult() { return marketUpdateResult; }
    public PaperTradingStepResult getTradingStepResult() { return tradingStepResult; }
    public boolean isUtcTradingDayRolled() { return utcTradingDayRolled; }
    public boolean getUtcTradingDayRolled() { return utcTradingDayRolled; }
    public LocalDate getPreviousUtcDate() { return previousUtcDate; }
    public LocalDate getCurrentUtcDate() { return currentUtcDate; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperRuntimeStepResult that)) return false;
        return utcTradingDayRolled == that.utcTradingDayRolled && stepType == that.stepType
                && runtime.equals(that.runtime) && marketUpdateResult.equals(that.marketUpdateResult)
                && Objects.equals(tradingStepResult, that.tradingStepResult)
                && previousUtcDate.equals(that.previousUtcDate)
                && currentUtcDate.equals(that.currentUtcDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepType, runtime, marketUpdateResult, tradingStepResult,
                utcTradingDayRolled, previousUtcDate, currentUtcDate);
    }
}
