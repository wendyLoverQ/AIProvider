package com.aiprovider.quant.audit.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.account.paper.PaperAccountUpdateResult;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingStepResult;
import com.aiprovider.quant.engine.paper.PaperTradingStepType;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionResult;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.portfolio.sizing.PositionSizingResult;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecision;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecisionStatus;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskViolation;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskViolationCode;
import com.aiprovider.quant.runtime.paper.DefaultPaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeConfig;
import com.aiprovider.quant.runtime.paper.PaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepType;
import com.aiprovider.quant.strategy.runtime.StrategyRuntimePosition;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecisionReason;
import com.aiprovider.quant.strategy.runtime.StrategySignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperOrderAuditEngineTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final String FEE_ASSET = "USDT";
    private static final Instant T0 = Instant.parse("2026-07-28T00:00:00Z");

    private final ExecutionOrderStateMachine orderMachine = new ExecutionOrderStateMachine();
    private final PaperAccountEngine accountEngine = new DefaultPaperAccountEngine();
    private final PaperRuntimeEngine runtimeEngine = new DefaultPaperRuntimeEngine();
    private final PaperOrderAuditEngine engine = new DefaultPaperOrderAuditEngine();
    private final PaperRuntimeSnapshot emptyRuntime =
            runtimeEngine.initialize(runtimeConfig(), List.of(), account());
    private final PaperRuntimeStepResult marketOnlyStep =
            runtimeEngine.onBookTicker(emptyRuntime, book(T0.plusSeconds(1)));

    @Test
    void initializesEmptyOrderHistory() {
        PaperOrderAuditLedger ledger = initialize(emptyRuntime, List.of());

        assertThat(ledger.getOrderHistory()).isEmpty();
        assertThat(ledger.getVersion()).isZero();
        assertThat(ledger.getInitializedAt()).isEqualTo(T0);
        assertThat(ledger.getLastUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void initializesLegalSeedOrderHistory() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        PaperRuntimeSnapshot runtime = runtimeWith(submitted);

        PaperOrderAuditLedger ledger =
                engine.initialize(runtime, List.of(submitted), T0.plusSeconds(10));

        assertThat(ledger.getOrderHistory()).containsExactly(submitted);
        assertThat(ledger.getVersion()).isZero();
    }

    @Test
    void rejectsDuplicateSeedClientOrderId() {
        ExecutionOrderSnapshot first = submitted("same", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot second = submitted("same", "exec-2", "2", T0.plusSeconds(6));

        assertError(
                () -> engine.initialize(runtimeWith(second), List.of(first, second), T0.plusSeconds(20)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID);
    }

    @Test
    void rejectsDuplicateSeedExecutionOrderId() {
        ExecutionOrderSnapshot first = submitted("order-1", "same", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot second = submitted("order-2", "same", "2", T0.plusSeconds(6));

        assertError(
                () -> engine.initialize(runtimeWith(second), List.of(first, second), T0.plusSeconds(20)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID);
    }

    @Test
    void rejectsSeedRequestedAtOrderMovingBackwards() {
        ExecutionOrderSnapshot first = submitted("order-1", "exec-1", "2", T0.plusSeconds(8));
        ExecutionOrderSnapshot second = submitted("order-2", "exec-2", "2", T0.plusSeconds(2));

        assertError(
                () -> engine.initialize(runtimeWith(second), List.of(first, second), T0.plusSeconds(20)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID);
    }

    @Test
    void rejectsSeedThatDoesNotReconcileWithSession() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));

        assertError(
                () -> engine.initialize(emptyRuntime, List.of(submitted), T0.plusSeconds(10)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_INITIAL_STATE_INCONSISTENT);
    }

    @Test
    void noTradingStepLeavesLedgerUnchanged() {
        PaperOrderAuditLedger ledger = initialize(emptyRuntime, List.of());

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, marketOnlyStep, T0.plusSeconds(2));

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getLedger()).isSameAs(ledger);
        assertThat(result.getPreviousOrder()).isNull();
        assertThat(result.getCurrentOrder()).isNull();
    }

    @Test
    void tradingStepWithoutExecutionOrderLeavesLedgerUnchanged() {
        PaperTradingStepResult trading = new PaperTradingStepResult(
                PaperTradingStepType.DUPLICATE_CANDLE_IGNORED,
                emptyRuntime.getTradingSession(), null, null, null, null, null, null);
        PaperRuntimeStepResult step = wrap(emptyRuntime, trading);
        PaperOrderAuditLedger ledger = initialize(emptyRuntime, List.of());

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, step, T0.plusSeconds(2));

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getLedger()).isSameAs(ledger);
    }

    @Test
    void appendsNewSubmittedOrder() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        PaperOrderAuditLedger ledger = initialize(emptyRuntime, List.of());

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, activeStep(submitted), T0.plusSeconds(10));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isNewOrder()).isTrue();
        assertThat(result.getPreviousOrder()).isNull();
        assertThat(result.getCurrentOrder()).isEqualTo(submitted);
        assertThat(result.getLedger().getOrderHistory()).containsExactly(submitted);
        assertThat(result.getLedger().getVersion()).isEqualTo(1);
    }

    @Test
    void appendsNewRejectedOrder() {
        ExecutionOrderSnapshot rejected = rejected("order-1", T0.plusSeconds(2), "RISK_LIMIT");
        PaperOrderAuditLedger ledger = initialize(emptyRuntime, List.of());

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, rejectedStep(rejected), T0.plusSeconds(10));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isNewOrder()).isTrue();
        assertThat(result.getLedger().getOrderHistory()).containsExactly(rejected);
        assertThat(result.getLedger().getVersion()).isEqualTo(1);
    }

    @Test
    void duplicateSnapshotRecordIsIdempotent() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        PaperOrderAuditUpdateResult first = engine.record(
                initialize(emptyRuntime, List.of()), activeStep(submitted), T0.plusSeconds(10));

        PaperOrderAuditUpdateResult duplicate =
                engine.record(first.getLedger(), activeStep(submitted), T0.plusSeconds(11));

        assertThat(duplicate.isApplied()).isFalse();
        assertThat(duplicate.getLedger()).isSameAs(first.getLedger());
        assertThat(duplicate.getPreviousOrder()).isEqualTo(submitted);
        assertThat(duplicate.getCurrentOrder()).isEqualTo(submitted);
        assertThat(duplicate.getLedger().getVersion()).isEqualTo(1);
    }

    @Test
    void evolvesSubmittedToPartiallyFilled() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot partial = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));
        PaperOrderAuditLedger ledger = recorded(submitted, T0.plusSeconds(10));

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, activeStep(partial), T0.plusSeconds(11));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.isNewOrder()).isFalse();
        assertThat(result.getPreviousOrder()).isEqualTo(submitted);
        assertThat(result.getCurrentOrder()).isEqualTo(partial);
        assertThat(result.getLedger().getVersion()).isEqualTo(2);
    }

    @Test
    void evolvesPartiallyFilledByAppendingFill() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "3", T0.plusSeconds(2));
        ExecutionOrderSnapshot firstPartial = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));
        ExecutionOrderSnapshot secondPartial = orderMachine.applyFill(
                firstPartial, fill("fill-2", "1", "101", "0.1", T0.plusSeconds(7)));
        PaperOrderAuditLedger ledger = recorded(firstPartial, T0.plusSeconds(10));

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, activeStep(secondPartial), T0.plusSeconds(11));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getLedger().getOrderHistory()).containsExactly(secondPartial);
        assertThat(result.getLedger().getVersion()).isEqualTo(2);
    }

    @Test
    void evolvesPartiallyFilledToFilled() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot partial = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));
        ExecutionOrderSnapshot filled = orderMachine.applyFill(
                partial, fill("fill-2", "1", "101", "0.1", T0.plusSeconds(7)));
        PaperOrderAuditLedger ledger = recorded(partial, T0.plusSeconds(10));

        PaperOrderAuditUpdateResult result =
                engine.record(ledger, filledStep(filled), T0.plusSeconds(11));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getLedger().getOrderHistory()).containsExactly(filled);
        assertThat(result.getLedger().getVersion()).isEqualTo(2);
    }

    @Test
    void rejectsModifiedExecutionOrderRequest() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot modifiedRequest = orderMachine.applyFill(
                submitted("order-1", "exec-1", "3", T0.plusSeconds(2)),
                fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));

        assertError(
                () -> engine.record(
                        recorded(submitted, T0.plusSeconds(10)),
                        activeStep(modifiedRequest), T0.plusSeconds(11)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID);
    }

    @Test
    void rejectsModifiedExecutionOrderId() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot modifiedId = orderMachine.applyFill(
                submitted("order-1", "exec-2", "2", T0.plusSeconds(2)),
                fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));

        assertError(
                () -> engine.record(
                        recorded(submitted, T0.plusSeconds(10)),
                        activeStep(modifiedId), T0.plusSeconds(11)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID);
    }

    @Test
    void rejectsModifiedHistoricalFill() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "3", T0.plusSeconds(2));
        ExecutionOrderSnapshot previous = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));
        ExecutionOrderSnapshot modified = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "101", "0.1", T0.plusSeconds(7)));

        assertError(
                () -> engine.record(
                        recorded(previous, T0.plusSeconds(10)),
                        activeStep(modified), T0.plusSeconds(11)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_FILL_HISTORY_CONFLICT);
    }

    @Test
    void rejectsFilledQuantityRollback() {
        OrderEvolution evolution = twoPartialFills();

        assertError(
                () -> engine.record(
                        recorded(evolution.second, T0.plusSeconds(10)),
                        activeStep(evolution.first), T0.plusSeconds(11)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID);
    }

    @Test
    void rejectsRemainingQuantityIncrease() {
        OrderEvolution evolution = twoPartialFills();

        assertThatThrownBy(() -> engine.record(
                recorded(evolution.second, T0.plusSeconds(10)),
                activeStep(evolution.first), T0.plusSeconds(11)))
                .isInstanceOfSatisfying(PaperOrderAuditException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID);
                    assertThat(exception.getMessage()).contains("cumulative quantities");
                });
    }

    @Test
    void rejectsAnyChangeAfterTerminalStatus() {
        ExecutionOrderSnapshot rejected = rejected("order-1", T0.plusSeconds(2), "RISK_LIMIT");
        ExecutionOrderSnapshot changed =
                rejected("order-1", T0.plusSeconds(2), "DIFFERENT_LIMIT");
        PaperOrderAuditLedger ledger = engine.initialize(
                runtimeWith(rejected), List.of(rejected), T0.plusSeconds(10));

        assertError(
                () -> engine.record(ledger, rejectedStep(changed), T0.plusSeconds(11)),
                PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID);
    }

    @Test
    void replacesExistingOrderAtItsOriginalPosition() {
        ExecutionOrderSnapshot first = submitted("order-1", "exec-1", "3", T0.plusSeconds(2));
        ExecutionOrderSnapshot second = submitted("order-2", "exec-2", "2", T0.plusSeconds(8));
        PaperOrderAuditLedger ledger = recorded(first, T0.plusSeconds(12));
        ledger = engine.record(ledger, activeStep(second), T0.plusSeconds(13)).getLedger();
        ExecutionOrderSnapshot firstPartial = orderMachine.applyFill(
                first, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(14)));

        PaperOrderAuditLedger updated =
                engine.record(ledger, activeStep(firstPartial), T0.plusSeconds(15)).getLedger();

        assertThat(updated.getOrderHistory()).containsExactly(firstPartial, second);
        assertThat(updated.getVersion()).isEqualTo(3);
    }

    @Test
    void reconcilesCanonicalLedgerAsConsistent() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot filled = orderMachine.applyFill(
                submitted, fill("fill-1", "2", "100", "0.2", T0.plusSeconds(6)));
        PaperRuntimeSnapshot runtime = runtimeWith(filled);
        PaperOrderAuditLedger ledger =
                engine.initialize(runtime, List.of(filled), T0.plusSeconds(10));

        PaperReconciliationReport report =
                engine.reconcile(ledger, runtime, T0.plusSeconds(11));

        assertThat(report.getStatus()).isEqualTo(PaperReconciliationStatus.CONSISTENT);
        assertThat(report.getOrderCount()).isEqualTo(1);
    }

    @Test
    void returnsInconsistentReportForTamperedLedgerState() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        ExecutionOrderSnapshot filled = orderMachine.applyFill(
                submitted, fill("fill-1", "2", "100", "0.2", T0.plusSeconds(6)));
        PaperRuntimeSnapshot runtime = runtimeWith(filled);
        PaperOrderAuditLedger tampered = new PaperOrderAuditLedger(
                config().getSessionId(), PROVIDER, MARKET_TYPE, SYMBOL,
                List.of(submitted), 1, T0, T0.plusSeconds(10));

        PaperReconciliationReport report =
                engine.reconcile(tampered, runtime, T0.plusSeconds(11));

        assertThat(report.getStatus()).isEqualTo(PaperReconciliationStatus.INCONSISTENT);
        assertThat(report.getViolations()).isNotEmpty();
    }

    @Test
    void ledgerDefensivelyCopiesAndExposesReadOnlyHistory() {
        ExecutionOrderSnapshot submitted = submitted("order-1", "exec-1", "2", T0.plusSeconds(2));
        List<ExecutionOrderSnapshot> mutable = new ArrayList<>();
        mutable.add(submitted);
        PaperOrderAuditLedger ledger = new PaperOrderAuditLedger(
                config().getSessionId(), PROVIDER, MARKET_TYPE, SYMBOL,
                mutable, 0, T0, T0);

        mutable.clear();

        assertThat(ledger.getOrderHistory()).containsExactly(submitted);
        assertThatThrownBy(() -> ledger.getOrderHistory().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconciliationFailurePreservesCause() {
        IllegalStateException cause = new IllegalStateException("broken reconciliation");
        PaperReconciliationEngine broken = request -> {
            throw cause;
        };
        PaperOrderAuditEngine brokenEngine = new DefaultPaperOrderAuditEngine(broken);

        assertThatThrownBy(() -> brokenEngine.initialize(emptyRuntime, List.of(), T0))
                .isInstanceOfSatisfying(PaperOrderAuditException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            PaperOrderAuditException.PAPER_ORDER_AUDIT_RECONCILIATION_FAILED);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private PaperOrderAuditLedger initialize(
            PaperRuntimeSnapshot runtime,
            List<ExecutionOrderSnapshot> history) {
        return engine.initialize(runtime, history, runtime.getTradingSession().getLastUpdatedAt());
    }

    private PaperOrderAuditLedger recorded(ExecutionOrderSnapshot order, Instant recordedAt) {
        return new PaperOrderAuditLedger(
                config().getSessionId(), PROVIDER, MARKET_TYPE, SYMBOL,
                List.of(order), 1, T0, recordedAt);
    }

    private PaperRuntimeStepResult activeStep(ExecutionOrderSnapshot order) {
        PaperRuntimeSnapshot runtime = runtimeWith(order);
        PaperTradingStepResult trading = new PaperTradingStepResult(
                PaperTradingStepType.PENDING_ORDER_ACTIVE,
                runtime.getTradingSession(), null, null, null, order, null, null);
        return wrap(runtime, trading);
    }

    private PaperRuntimeStepResult filledStep(ExecutionOrderSnapshot order) {
        PaperRuntimeSnapshot runtime = runtimeWith(order);
        ExecutionFill latestFill = order.getFills().get(order.getFills().size() - 1);
        PaperAccountUpdateResult accountUpdate =
                new PaperAccountUpdateResult(runtime.getTradingSession().getPaperAccountSnapshot(), true);
        SimulatedExecutionResult simulated = new SimulatedExecutionResult(
                order, latestFill, order.getRequest().getOrderSide(),
                new BigDecimal("99"), new BigDecimal("100"), BigDecimal.ZERO,
                latestFill.getQuantity(), latestFill.getQuantity(),
                order.getRemainingQuantity(), true);
        PaperTradingStepResult trading = new PaperTradingStepResult(
                PaperTradingStepType.ORDER_FILLED, runtime.getTradingSession(),
                null, null, null, order, simulated, accountUpdate);
        return wrap(runtime, trading);
    }

    private PaperRuntimeStepResult rejectedStep(ExecutionOrderSnapshot order) {
        PaperRuntimeSnapshot runtime = runtimeWith(order);
        PaperTradingStepResult trading = new PaperTradingStepResult(
                PaperTradingStepType.RISK_REJECTED,
                runtime.getTradingSession(),
                signalDecision(),
                sizingResult(),
                rejectedRiskDecision(order.getRequest().getClientOrderId()),
                order, null, null);
        return wrap(runtime, trading);
    }

    private PaperRuntimeStepResult wrap(
            PaperRuntimeSnapshot runtime,
            PaperTradingStepResult tradingStep) {
        LocalDate date = LocalDate.of(2026, 7, 28);
        return new PaperRuntimeStepResult(
                PaperRuntimeStepType.CLOSED_CANDLE_PROCESSED,
                runtime,
                marketOnlyStep.getMarketUpdateResult(),
                tradingStep,
                false,
                date,
                date);
    }

    private PaperRuntimeSnapshot runtimeWith(ExecutionOrderSnapshot order) {
        PaperAccountSnapshot account = accountFor(order);
        boolean active = order.getStatus() == ExecutionOrderStatus.SUBMITTED
                || order.getStatus() == ExecutionOrderStatus.PARTIALLY_FILLED;
        PaperTradingSessionSnapshot session = new PaperTradingSessionSnapshot(
                config(), account, active ? order : null, order,
                null, null, null, null, account.getLastUpdatedAt());
        return new PaperRuntimeSnapshot(
                runtimeConfig(), emptyRuntime.getMarketState(), session, null, null);
    }

    private PaperAccountSnapshot accountFor(ExecutionOrderSnapshot order) {
        PaperAccountSnapshot result = account();
        if (order.getFills().isEmpty()) {
            return accountEngine.markToMarket(
                    result, SYMBOL, new BigDecimal("100"), order.getLastUpdatedAt());
        }
        for (ExecutionFill fill : order.getFills()) {
            result = accountEngine.applyFill(result, order.getRequest(), fill).getAccount();
        }
        return result;
    }

    private ExecutionOrderSnapshot submitted(
            String clientOrderId,
            String executionOrderId,
            String quantity,
            Instant requestedAt) {
        ExecutionOrderRequest request = new ExecutionOrderRequest(
                clientOrderId, PROVIDER, MARKET_TYPE, SYMBOL, ExecutionOrderType.MARKET,
                OrderSide.BUY, PositionSide.LONG, new BigDecimal(quantity), false, requestedAt);
        ExecutionOrderSnapshot created = orderMachine.create(request);
        ExecutionOrderSnapshot accepted =
                orderMachine.accept(created, requestedAt.plusSeconds(1));
        return orderMachine.submit(accepted, executionOrderId, requestedAt.plusSeconds(2));
    }

    private ExecutionOrderSnapshot rejected(
            String clientOrderId,
            Instant requestedAt,
            String errorCode) {
        ExecutionOrderRequest request = new ExecutionOrderRequest(
                clientOrderId, PROVIDER, MARKET_TYPE, SYMBOL, ExecutionOrderType.MARKET,
                OrderSide.BUY, PositionSide.LONG, new BigDecimal("2"), false, requestedAt);
        return orderMachine.reject(
                orderMachine.create(request), errorCode, "risk rejected", requestedAt.plusSeconds(1));
    }

    private ExecutionFill fill(
            String fillId,
            String quantity,
            String price,
            String fee,
            Instant filledAt) {
        return new ExecutionFill(
                fillId, new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(fee), FEE_ASSET, filledAt);
    }

    private OrderEvolution twoPartialFills() {
        ExecutionOrderSnapshot submitted =
                submitted("order-1", "exec-1", "3", T0.plusSeconds(2));
        ExecutionOrderSnapshot first = orderMachine.applyFill(
                submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(6)));
        ExecutionOrderSnapshot second = orderMachine.applyFill(
                first, fill("fill-2", "1", "101", "0.1", T0.plusSeconds(7)));
        return new OrderEvolution(first, second);
    }

    private PaperAccountSnapshot account() {
        return accountEngine.initialize(
                "account-1", PROVIDER, MARKET_TYPE, FEE_ASSET,
                new BigDecimal("10000"), LocalDate.of(2026, 7, 28), T0);
    }

    private PaperRuntimeConfig runtimeConfig() {
        return new PaperRuntimeConfig(
                new RuntimeMarketKey(PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1),
                10, config());
    }

    private PaperTradingSessionConfig config() {
        return new PaperTradingSessionConfig(
                "session-1", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "TEST", "1", Map.of("period", 3),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                new BigDecimal("2"), null,
                new MarketOrderQuantityRules(
                        PROVIDER, MARKET_TYPE, SYMBOL, FEE_ASSET, 3,
                        new BigDecimal("0.001"), new BigDecimal("0.001"),
                        new BigDecimal("1000"), new BigDecimal("5")),
                BigDecimal.ONE,
                new PreTradeRiskPolicy(
                        new BigDecimal("0.90"), new BigDecimal("0.90"),
                        new BigDecimal("0.01"), new BigDecimal("0.50"), 5),
                new SimulatedExecutionPolicy(
                        new BigDecimal("0.001"), FEE_ASSET, BigDecimal.ZERO));
    }

    private StreamBookTickerEvent book(Instant eventTime) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL);
        event.setEventTime(eventTime);
        event.setBidPrice(new BigDecimal("99"));
        event.setBidQuantity(BigDecimal.TEN);
        event.setAskPrice(new BigDecimal("100"));
        event.setAskQuantity(BigDecimal.TEN);
        return event;
    }

    private StrategySignalDecision signalDecision() {
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET_TYPE);
        candle.setSymbol(SYMBOL);
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(T0);
        candle.setCloseTime(T0.plusSeconds(59));
        candle.setOpenPrice(new BigDecimal("100"));
        candle.setHighPrice(new BigDecimal("100"));
        candle.setLowPrice(new BigDecimal("100"));
        candle.setClosePrice(new BigDecimal("100"));
        candle.setVolume(BigDecimal.TEN);
        candle.setQuoteVolume(new BigDecimal("1000"));
        candle.setTradeCount(10);
        candle.setTakerBuyBaseVolume(BigDecimal.ONE);
        candle.setTakerBuyQuoteVolume(new BigDecimal("100"));
        return new StrategySignalDecision(
                "TEST", "1", Map.of("period", 3), PROVIDER, MARKET_TYPE, SYMBOL,
                KlineInterval.M1, StrategyRuntimePosition.FLAT, StrategySignalType.ENTER_LONG,
                0, candle, StrategySignalDecisionReason.ENTRY_RULE_MATCHED);
    }

    private PositionSizingResult sizingResult() {
        return new PositionSizingResult(
                PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.1"),
                new BigDecimal("100.1"), new BigDecimal("10000"), new BigDecimal("10000"),
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("0.01"),
                new BigDecimal("0.001"), new BigDecimal("0.001"),
                new BigDecimal("1000"), new BigDecimal("5"), FEE_ASSET);
    }

    private PreTradeRiskDecision rejectedRiskDecision(String clientOrderId) {
        return new PreTradeRiskDecision(
                PreTradeRiskDecisionStatus.REJECTED, clientOrderId, OrderSide.BUY,
                BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.1"), BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("10000"),
                new BigDecimal("9999.9"), new BigDecimal("10000"),
                new BigDecimal("9899.9"), new BigDecimal("0.01"),
                new BigDecimal("0.01"), new BigDecimal("0.98999"), BigDecimal.ZERO,
                List.of(new PreTradeRiskViolation(
                        PreTradeRiskViolationCode.ORDER_NOTIONAL_LIMIT_EXCEEDED,
                        new BigDecimal("0.01"), new BigDecimal("0.001"), "limit")));
    }

    private void assertError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PaperOrderAuditException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
                    assertThat(exception.getMessage()).contains(errorCode);
                });
    }

    private static final class OrderEvolution {
        private final ExecutionOrderSnapshot first;
        private final ExecutionOrderSnapshot second;

        private OrderEvolution(
                ExecutionOrderSnapshot first,
                ExecutionOrderSnapshot second) {
            this.first = first;
            this.second = second;
        }
    }
}
