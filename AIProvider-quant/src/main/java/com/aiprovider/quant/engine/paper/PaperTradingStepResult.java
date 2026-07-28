package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.PaperAccountUpdateResult;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionResult;
import com.aiprovider.quant.portfolio.sizing.PositionSizingResult;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecision;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecisionStatus;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalType;

import java.util.Objects;

public final class PaperTradingStepResult {
    private final PaperTradingStepType stepType;
    private final PaperTradingSessionSnapshot session;
    private final StrategySignalDecision signalDecision;
    private final PositionSizingResult positionSizingResult;
    private final PreTradeRiskDecision preTradeRiskDecision;
    private final ExecutionOrderSnapshot executionOrderSnapshot;
    private final SimulatedExecutionResult simulatedExecutionResult;
    private final PaperAccountUpdateResult paperAccountUpdateResult;

    public PaperTradingStepResult(
            PaperTradingStepType stepType,
            PaperTradingSessionSnapshot session,
            StrategySignalDecision signalDecision,
            PositionSizingResult positionSizingResult,
            PreTradeRiskDecision preTradeRiskDecision,
            ExecutionOrderSnapshot executionOrderSnapshot,
            SimulatedExecutionResult simulatedExecutionResult,
            PaperAccountUpdateResult paperAccountUpdateResult) {
        this.stepType = Objects.requireNonNull(stepType, "stepType");
        this.session = Objects.requireNonNull(session, "session");
        this.signalDecision = signalDecision;
        this.positionSizingResult = positionSizingResult;
        this.preTradeRiskDecision = preTradeRiskDecision;
        this.executionOrderSnapshot = executionOrderSnapshot;
        this.simulatedExecutionResult = simulatedExecutionResult;
        this.paperAccountUpdateResult = paperAccountUpdateResult;
        validateConsistency();
    }

    private void validateConsistency() {
        boolean noSignalFlow = signalDecision == null && positionSizingResult == null
                && preTradeRiskDecision == null;
        if (stepType == PaperTradingStepType.DUPLICATE_CANDLE_IGNORED
                && (!noSignalFlow || executionOrderSnapshot != null
                || simulatedExecutionResult != null || paperAccountUpdateResult != null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.PENDING_ORDER_ACTIVE
                && (!noSignalFlow || executionOrderSnapshot == null
                || (executionOrderSnapshot.getStatus() != ExecutionOrderStatus.SUBMITTED
                && executionOrderSnapshot.getStatus() != ExecutionOrderStatus.PARTIALLY_FILLED)
                || simulatedExecutionResult != null || paperAccountUpdateResult != null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.SIGNAL_HOLD
                && (signalDecision == null || signalDecision.getSignalType() != StrategySignalType.HOLD
                || positionSizingResult != null || preTradeRiskDecision != null
                || executionOrderSnapshot != null || simulatedExecutionResult != null
                || paperAccountUpdateResult != null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.RISK_REJECTED
                && (signalDecision == null || preTradeRiskDecision == null
                || preTradeRiskDecision.getDecisionStatus() != PreTradeRiskDecisionStatus.REJECTED
                || executionOrderSnapshot == null
                || executionOrderSnapshot.getStatus() != ExecutionOrderStatus.REJECTED
                || simulatedExecutionResult != null || paperAccountUpdateResult != null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.RISK_REJECTED
                && !signalAndSizingMatch()) {
            invalid();
        }
        if ((stepType == PaperTradingStepType.ENTRY_ORDER_SUBMITTED
                || stepType == PaperTradingStepType.EXIT_ORDER_SUBMITTED)
                && (signalDecision == null || preTradeRiskDecision == null
                || preTradeRiskDecision.getDecisionStatus() != PreTradeRiskDecisionStatus.APPROVED
                || executionOrderSnapshot == null
                || executionOrderSnapshot.getStatus() != ExecutionOrderStatus.SUBMITTED
                || simulatedExecutionResult != null || paperAccountUpdateResult != null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.ENTRY_ORDER_SUBMITTED
                && (signalDecision.getSignalType() != StrategySignalType.ENTER_LONG
                || positionSizingResult == null)) {
            invalid();
        }
        if (stepType == PaperTradingStepType.EXIT_ORDER_SUBMITTED
                && (signalDecision.getSignalType() != StrategySignalType.EXIT_LONG
                || positionSizingResult != null)) {
            invalid();
        }
        if ((stepType == PaperTradingStepType.ORDER_PARTIALLY_FILLED
                || stepType == PaperTradingStepType.ORDER_FILLED)
                && (!noSignalFlow || executionOrderSnapshot == null
                || simulatedExecutionResult == null || paperAccountUpdateResult == null
                || !paperAccountUpdateResult.isApplied()
                || !executionOrderSnapshot.equals(simulatedExecutionResult.getOrderSnapshot())
                || !session.getPaperAccountSnapshot().equals(paperAccountUpdateResult.getAccount()))) {
            invalid();
        }
        if (stepType == PaperTradingStepType.ORDER_PARTIALLY_FILLED
                && (executionOrderSnapshot.getStatus() != ExecutionOrderStatus.PARTIALLY_FILLED
                || simulatedExecutionResult.isCompletelyFilled())) {
            invalid();
        }
        if (stepType == PaperTradingStepType.ORDER_FILLED
                && (executionOrderSnapshot.getStatus() != ExecutionOrderStatus.FILLED
                || !simulatedExecutionResult.isCompletelyFilled())) {
            invalid();
        }
    }

    private boolean signalAndSizingMatch() {
        return signalDecision.getSignalType() == StrategySignalType.ENTER_LONG
                ? positionSizingResult != null
                : signalDecision.getSignalType() == StrategySignalType.EXIT_LONG
                && positionSizingResult == null;
    }

    private void invalid() {
        throw new PaperTradingException(PaperTradingException.PAPER_TRADING_STATE_INVALID,
                "stepType and result fields are inconsistent: " + stepType);
    }

    public PaperTradingStepType getStepType() { return stepType; }
    public PaperTradingSessionSnapshot getSession() { return session; }
    public StrategySignalDecision getSignalDecision() { return signalDecision; }
    public PositionSizingResult getPositionSizingResult() { return positionSizingResult; }
    public PreTradeRiskDecision getPreTradeRiskDecision() { return preTradeRiskDecision; }
    public ExecutionOrderSnapshot getExecutionOrderSnapshot() { return executionOrderSnapshot; }
    public SimulatedExecutionResult getSimulatedExecutionResult() { return simulatedExecutionResult; }
    public PaperAccountUpdateResult getPaperAccountUpdateResult() { return paperAccountUpdateResult; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperTradingStepResult that)) return false;
        return stepType == that.stepType && Objects.equals(session, that.session)
                && Objects.equals(signalDecision, that.signalDecision)
                && Objects.equals(positionSizingResult, that.positionSizingResult)
                && Objects.equals(preTradeRiskDecision, that.preTradeRiskDecision)
                && Objects.equals(executionOrderSnapshot, that.executionOrderSnapshot)
                && Objects.equals(simulatedExecutionResult, that.simulatedExecutionResult)
                && Objects.equals(paperAccountUpdateResult, that.paperAccountUpdateResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepType, session, signalDecision, positionSizingResult,
                preTradeRiskDecision, executionOrderSnapshot, simulatedExecutionResult,
                paperAccountUpdateResult);
    }
}
