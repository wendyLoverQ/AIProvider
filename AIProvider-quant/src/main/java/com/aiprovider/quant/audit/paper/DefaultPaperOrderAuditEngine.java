package com.aiprovider.quant.audit.paper;

import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingStepResult;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.reconciliation.paper.DefaultPaperReconciliationEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationRequest;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DefaultPaperOrderAuditEngine implements PaperOrderAuditEngine {
    private final PaperReconciliationEngine reconciliationEngine;

    public DefaultPaperOrderAuditEngine() {
        this(new DefaultPaperReconciliationEngine());
    }

    public DefaultPaperOrderAuditEngine(PaperReconciliationEngine reconciliationEngine) {
        if (reconciliationEngine == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "reconciliationEngine is required");
        }
        this.reconciliationEngine = reconciliationEngine;
    }

    @Override
    public PaperOrderAuditLedger initialize(
            PaperRuntimeSnapshot runtime,
            List<ExecutionOrderSnapshot> seedOrderHistory,
            Instant initializedAt) {
        if (runtime == null || seedOrderHistory == null || initializedAt == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "runtime, seedOrderHistory and initializedAt are required");
        }
        PaperTradingSessionSnapshot session = requireSession(runtime);
        if (initializedAt.isBefore(session.getLastUpdatedAt())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                    "initializedAt must not precede Session lastUpdatedAt");
        }
        PaperTradingSessionConfig config = session.getConfig();
        validateSeed(seedOrderHistory, config);
        PaperOrderAuditLedger ledger = new PaperOrderAuditLedger(
                config.getSessionId(), config.getProvider(), config.getMarketType(),
                config.getSymbol(), seedOrderHistory, 0L, initializedAt, initializedAt);
        PaperReconciliationReport report = callReconciliation(
                session, ledger.getOrderHistory(), initializedAt);
        if (report.getStatus() != PaperReconciliationStatus.CONSISTENT) {
            List<String> violationCodes = new ArrayList<>();
            report.getViolations().forEach(violation -> {
                String code = violation.getCode().name();
                if (!violationCodes.contains(code)) {
                    violationCodes.add(code);
                }
            });
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_INITIAL_STATE_INCONSISTENT,
                    "seed OrderHistory is inconsistent with current Session; violations="
                            + violationCodes);
        }
        return ledger;
    }

    @Override
    public PaperOrderAuditUpdateResult record(
            PaperOrderAuditLedger ledger,
            PaperRuntimeStepResult runtimeStepResult,
            Instant recordedAt) {
        if (ledger == null || runtimeStepResult == null || recordedAt == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "ledger, runtimeStepResult and recordedAt are required");
        }
        PaperRuntimeSnapshot runtime = runtimeStepResult.getRuntime();
        requireContext(ledger, runtime);
        if (recordedAt.isBefore(ledger.getLastUpdatedAt())
                || recordedAt.isBefore(runtime.getTradingSession().getLastUpdatedAt())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                    "recordedAt must not precede ledger or Session lastUpdatedAt");
        }
        PaperTradingStepResult tradingStep = runtimeStepResult.getTradingStepResult();
        ExecutionOrderSnapshot current =
                tradingStep == null ? null : tradingStep.getExecutionOrderSnapshot();
        if (current == null) {
            return new PaperOrderAuditUpdateResult(ledger, false, null, null, false);
        }
        if (recordedAt.isBefore(current.getLastUpdatedAt())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                    "recordedAt must not precede observed order LastUpdatedAt");
        }
        validateObservedRuntimeOrder(runtime.getTradingSession(), current);
        validateOrderContext(ledger, current);

        int existingIndex = indexOf(ledger.getOrderHistory(), clientOrderId(current));
        if (existingIndex < 0) {
            validateNewOrder(ledger, runtime.getTradingSession(), current);
            List<ExecutionOrderSnapshot> history =
                    new ArrayList<>(ledger.getOrderHistory());
            history.add(current);
            PaperOrderAuditLedger updated = updatedLedger(ledger, history, recordedAt);
            return new PaperOrderAuditUpdateResult(updated, true, null, current, true);
        }

        ExecutionOrderSnapshot previous = ledger.getOrderHistory().get(existingIndex);
        if (previous.equals(current)) {
            return new PaperOrderAuditUpdateResult(
                    ledger, false, previous, current, false);
        }
        validateExistingOrder(previous, current);
        List<ExecutionOrderSnapshot> history = new ArrayList<>(ledger.getOrderHistory());
        history.set(existingIndex, current);
        PaperOrderAuditLedger updated = updatedLedger(ledger, history, recordedAt);
        return new PaperOrderAuditUpdateResult(updated, true, previous, current, false);
    }

    @Override
    public PaperReconciliationReport reconcile(
            PaperOrderAuditLedger ledger,
            PaperRuntimeSnapshot runtime,
            Instant reconciledAt) {
        if (ledger == null || runtime == null || reconciledAt == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "ledger, runtime and reconciledAt are required");
        }
        requireContext(ledger, runtime);
        return callReconciliation(
                runtime.getTradingSession(), ledger.getOrderHistory(), reconciledAt);
    }

    private void validateSeed(
            List<ExecutionOrderSnapshot> seed,
            PaperTradingSessionConfig config) {
        Set<String> clientOrderIds = new HashSet<>();
        Set<String> executionOrderIds = new HashSet<>();
        Instant previousRequestedAt = null;
        for (ExecutionOrderSnapshot order : seed) {
            if (order == null || order.getRequest() == null) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                        "seed OrderHistory must not contain null");
            }
            ExecutionOrderRequest request = order.getRequest();
            if (request.getProvider() != config.getProvider()
                    || request.getMarketType() != config.getMarketType()
                    || !config.getSymbol().equals(request.getSymbol())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_CONTEXT_MISMATCH,
                        "seed order context does not match Session");
            }
            if (!clientOrderIds.add(request.getClientOrderId())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID,
                        "duplicate ClientOrderId=" + request.getClientOrderId());
            }
            if (order.getExecutionOrderId() != null
                    && !executionOrderIds.add(order.getExecutionOrderId())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID,
                        "duplicate ExecutionOrderId=" + order.getExecutionOrderId());
            }
            if (previousRequestedAt != null
                    && request.getRequestedAt().isBefore(previousRequestedAt)) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                        "seed OrderHistory requestedAt must be nondecreasing");
            }
            previousRequestedAt = request.getRequestedAt();
        }
    }

    private void validateObservedRuntimeOrder(
            PaperTradingSessionSnapshot session,
            ExecutionOrderSnapshot order) {
        if (!order.equals(session.getLastOrderSnapshot())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "Session lastOrderSnapshot must equal the observed order");
        }
    }

    private void validateNewOrder(
            PaperOrderAuditLedger ledger,
            PaperTradingSessionSnapshot session,
            ExecutionOrderSnapshot order) {
        ExecutionOrderStatus status = order.getStatus();
        if (status != ExecutionOrderStatus.SUBMITTED
                && status != ExecutionOrderStatus.REJECTED) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "first observable status must be SUBMITTED or REJECTED");
        }
        if (status == ExecutionOrderStatus.SUBMITTED
                && !order.equals(session.getPendingOrderSnapshot())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "SUBMITTED order must equal Session pendingOrderSnapshot");
        }
        if (status == ExecutionOrderStatus.REJECTED
                && session.getPendingOrderSnapshot() != null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "REJECTED order requires an empty Session pendingOrderSnapshot");
        }
        if (!ledger.getOrderHistory().isEmpty()) {
            ExecutionOrderSnapshot last =
                    ledger.getOrderHistory().get(ledger.getOrderHistory().size() - 1);
            if (order.getRequest().getRequestedAt()
                    .isBefore(last.getRequest().getRequestedAt())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                        "new order requestedAt must preserve canonical OrderHistory order");
            }
        }
        for (ExecutionOrderSnapshot existing : ledger.getOrderHistory()) {
            if (order.getExecutionOrderId() != null
                    && order.getExecutionOrderId().equals(existing.getExecutionOrderId())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID,
                        "duplicate ExecutionOrderId=" + order.getExecutionOrderId());
            }
        }
    }

    private void validateExistingOrder(
            ExecutionOrderSnapshot previous,
            ExecutionOrderSnapshot current) {
        if (!previous.getRequest().equals(current.getRequest())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "ExecutionOrderRequest must not change");
        }
        if (!Objects.equals(previous.getExecutionOrderId(), current.getExecutionOrderId())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "ExecutionOrderId must not change");
        }
        if (compare(current.getFilledQuantity(), previous.getFilledQuantity()) < 0
                || compare(current.getCumulativeFee(), previous.getCumulativeFee()) < 0
                || compare(current.getRemainingQuantity(), previous.getRemainingQuantity()) > 0) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "order cumulative quantities or fee moved backwards");
        }
        validateFillPrefix(previous.getFills(), current.getFills());
        if (current.getLastUpdatedAt().isBefore(previous.getLastUpdatedAt())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                    "order LastUpdatedAt moved backwards");
        }
        if (terminal(previous.getStatus())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "terminal order must not change");
        }
        ExecutionOrderStatus oldStatus = previous.getStatus();
        ExecutionOrderStatus newStatus = current.getStatus();
        boolean valid = oldStatus == ExecutionOrderStatus.SUBMITTED
                && (newStatus == ExecutionOrderStatus.PARTIALLY_FILLED
                || newStatus == ExecutionOrderStatus.FILLED)
                || oldStatus == ExecutionOrderStatus.PARTIALLY_FILLED
                && (newStatus == ExecutionOrderStatus.PARTIALLY_FILLED
                || newStatus == ExecutionOrderStatus.FILLED);
        if (!valid) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "order status transition is not allowed: " + oldStatus + " -> " + newStatus);
        }
        if (oldStatus == ExecutionOrderStatus.PARTIALLY_FILLED
                && newStatus == ExecutionOrderStatus.PARTIALLY_FILLED
                && current.getFills().size() == previous.getFills().size()) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TRANSITION_INVALID,
                    "PARTIALLY_FILLED repetition requires a new Fill");
        }
    }

    private void validateFillPrefix(
            List<ExecutionFill> previous,
            List<ExecutionFill> current) {
        if (current.size() < previous.size()) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_FILL_HISTORY_CONFLICT,
                    "existing Fill history was removed");
        }
        for (int index = 0; index < previous.size(); index++) {
            if (!previous.get(index).equals(current.get(index))) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_FILL_HISTORY_CONFLICT,
                        "existing Fill history changed at index=" + index);
            }
        }
    }

    private PaperOrderAuditLedger updatedLedger(
            PaperOrderAuditLedger ledger,
            List<ExecutionOrderSnapshot> orderHistory,
            Instant recordedAt) {
        return new PaperOrderAuditLedger(
                ledger.getSessionId(), ledger.getProvider(), ledger.getMarketType(),
                ledger.getSymbol(), orderHistory, Math.addExact(ledger.getVersion(), 1L),
                ledger.getInitializedAt(), recordedAt);
    }

    private PaperTradingSessionSnapshot requireSession(PaperRuntimeSnapshot runtime) {
        if (runtime.getTradingSession() == null
                || runtime.getTradingSession().getConfig() == null
                || runtime.getTradingSession().getLastUpdatedAt() == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "runtime Session is missing required fields");
        }
        return runtime.getTradingSession();
    }

    private void requireContext(
            PaperOrderAuditLedger ledger,
            PaperRuntimeSnapshot runtime) {
        PaperTradingSessionConfig config = requireSession(runtime).getConfig();
        if (!ledger.getSessionId().equals(config.getSessionId())
                || ledger.getProvider() != config.getProvider()
                || ledger.getMarketType() != config.getMarketType()
                || !ledger.getSymbol().equals(config.getSymbol())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_CONTEXT_MISMATCH,
                    "Ledger context does not match Runtime Session");
        }
    }

    private void validateOrderContext(
            PaperOrderAuditLedger ledger,
            ExecutionOrderSnapshot order) {
        if (order.getRequest() == null
                || order.getRequest().getProvider() != ledger.getProvider()
                || order.getRequest().getMarketType() != ledger.getMarketType()
                || !ledger.getSymbol().equals(order.getRequest().getSymbol())) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_CONTEXT_MISMATCH,
                    "observed order context does not match Ledger");
        }
    }

    private PaperReconciliationReport callReconciliation(
            PaperTradingSessionSnapshot session,
            List<ExecutionOrderSnapshot> orderHistory,
            Instant reconciledAt) {
        try {
            PaperReconciliationReport report = reconciliationEngine.reconcile(
                    new PaperReconciliationRequest(session, orderHistory, reconciledAt));
            if (report == null) {
                throw new IllegalStateException("PaperReconciliationEngine returned null");
            }
            return report;
        } catch (PaperOrderAuditException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaperOrderAuditException(
                    PaperOrderAuditException.PAPER_ORDER_AUDIT_RECONCILIATION_FAILED,
                    "Paper reconciliation failed", exception);
        }
    }

    private int indexOf(List<ExecutionOrderSnapshot> history, String clientOrderId) {
        for (int index = 0; index < history.size(); index++) {
            if (clientOrderId.equals(clientOrderId(history.get(index)))) {
                return index;
            }
        }
        return -1;
    }

    private String clientOrderId(ExecutionOrderSnapshot order) {
        return order.getRequest().getClientOrderId();
    }

    private boolean terminal(ExecutionOrderStatus status) {
        return status == ExecutionOrderStatus.FILLED
                || status == ExecutionOrderStatus.REJECTED
                || status == ExecutionOrderStatus.CANCELED
                || status == ExecutionOrderStatus.FAILED;
    }

    private int compare(BigDecimal left, BigDecimal right) {
        return left.compareTo(right);
    }

    private PaperOrderAuditException error(String code, String message) {
        return new PaperOrderAuditException(code, message);
    }
}
