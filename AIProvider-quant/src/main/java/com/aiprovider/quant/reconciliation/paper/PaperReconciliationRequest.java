package com.aiprovider.quant.reconciliation.paper;

import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaperReconciliationRequest {
    private final PaperTradingSessionSnapshot session;
    private final List<ExecutionOrderSnapshot> orderHistory;
    private final Instant reconciledAt;

    public PaperReconciliationRequest(
            PaperTradingSessionSnapshot session,
            List<ExecutionOrderSnapshot> orderHistory,
            Instant reconciledAt) {
        this.session = session;
        this.orderHistory = orderHistory == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(orderHistory));
        this.reconciledAt = reconciledAt;
    }

    public PaperTradingSessionSnapshot getSession() {
        return session;
    }

    public List<ExecutionOrderSnapshot> getOrderHistory() {
        return orderHistory;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }
}
