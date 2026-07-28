package com.aiprovider.quant.audit.paper;

import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;

import java.util.Objects;

/** Immutable result of applying one observable runtime order snapshot. */
public final class PaperOrderAuditUpdateResult {
    private final PaperOrderAuditLedger ledger;
    private final boolean applied;
    private final ExecutionOrderSnapshot previousOrder;
    private final ExecutionOrderSnapshot currentOrder;
    private final boolean newOrder;

    public PaperOrderAuditUpdateResult(
            PaperOrderAuditLedger ledger,
            boolean applied,
            ExecutionOrderSnapshot previousOrder,
            ExecutionOrderSnapshot currentOrder,
            boolean newOrder) {
        if (ledger == null) {
            throw invalid("ledger is required");
        }
        if (!applied) {
            if (newOrder || !Objects.equals(previousOrder, currentOrder)) {
                throw invalid("an unapplied result cannot describe an order change");
            }
        } else if (currentOrder == null) {
            throw invalid("an applied result requires currentOrder");
        } else if (newOrder) {
            if (previousOrder != null) {
                throw invalid("a new order cannot have previousOrder");
            }
        } else if (previousOrder == null
                || previousOrder.equals(currentOrder)
                || !clientOrderId(previousOrder).equals(clientOrderId(currentOrder))) {
            throw invalid("an updated order requires distinct snapshots for one ClientOrderId");
        }
        if (currentOrder != null && !ledger.getOrderHistory().contains(currentOrder)) {
            throw invalid("ledger must contain currentOrder");
        }
        this.ledger = ledger;
        this.applied = applied;
        this.previousOrder = previousOrder;
        this.currentOrder = currentOrder;
        this.newOrder = newOrder;
    }

    public PaperOrderAuditLedger getLedger() { return ledger; }
    public boolean isApplied() { return applied; }
    public boolean getApplied() { return applied; }
    public ExecutionOrderSnapshot getPreviousOrder() { return previousOrder; }
    public ExecutionOrderSnapshot getCurrentOrder() { return currentOrder; }
    public boolean isNewOrder() { return newOrder; }
    public boolean getNewOrder() { return newOrder; }

    private static String clientOrderId(ExecutionOrderSnapshot order) {
        return order.getRequest().getClientOrderId();
    }

    private static PaperOrderAuditException invalid(String message) {
        return new PaperOrderAuditException(
                PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID, message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperOrderAuditUpdateResult that)) return false;
        return applied == that.applied
                && newOrder == that.newOrder
                && ledger.equals(that.ledger)
                && Objects.equals(previousOrder, that.previousOrder)
                && Objects.equals(currentOrder, that.currentOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ledger, applied, previousOrder, currentOrder, newOrder);
    }
}
