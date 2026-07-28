package com.aiprovider.quant.execution.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ExecutionOrderStateMachine {
    public ExecutionOrderSnapshot create(ExecutionOrderRequest request) {
        if (request == null) throw error("EXECUTION_ORDER_REQUEST_INVALID", "request is null");
        return ExecutionOrderSnapshot.created(request);
    }

    public ExecutionOrderSnapshot accept(ExecutionOrderSnapshot snapshot, Instant acceptedAt) {
        require(snapshot, ExecutionOrderStatus.CREATED);
        requireTime(snapshot, acceptedAt, snapshot.getRequest().getRequestedAt());
        return transition(snapshot, ExecutionOrderStatus.ACCEPTED, null, acceptedAt, null,
                acceptedAt, null, null, null);
    }

    public ExecutionOrderSnapshot submit(ExecutionOrderSnapshot snapshot, String executionOrderId, Instant submittedAt) {
        require(snapshot, ExecutionOrderStatus.ACCEPTED);
        if (blank(executionOrderId)) throw error("EXECUTION_ORDER_ID_REQUIRED", "executionOrderId is blank");
        requireTime(snapshot, submittedAt, snapshot.getAcceptedAt());
        return transition(snapshot, ExecutionOrderStatus.SUBMITTED, executionOrderId, submittedAt, null,
                snapshot.getAcceptedAt(), submittedAt, null, null);
    }

    public ExecutionOrderSnapshot applyFill(ExecutionOrderSnapshot snapshot, ExecutionFill fill) {
        if (snapshot == null || fill == null) {
            if (fill == null) throw error("EXECUTION_ORDER_FILL_INVALID", "fill is null");
            require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        }
        require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        if (snapshot.getFills().stream().anyMatch(existing -> existing.getFillId().equals(fill.getFillId()))) {
            throw error("EXECUTION_ORDER_DUPLICATE_FILL", "fillId=" + fill.getFillId());
        }
        Instant previousEvent = snapshot.getFills().isEmpty() ? snapshot.getSubmittedAt()
                : snapshot.getFills().get(snapshot.getFills().size() - 1).getFilledAt();
        requireTime(snapshot, fill.getFilledAt(), previousEvent);
        BigDecimal filled = snapshot.getFilledQuantity().add(fill.getQuantity());
        int quantityComparison = filled.compareTo(snapshot.getRequest().getQuantity());
        if (quantityComparison > 0) throw error("EXECUTION_ORDER_OVERFILLED", "filledQuantity=" + filled);
        BigDecimal remaining = snapshot.getRequest().getQuantity().subtract(filled);
        BigDecimal average = weightedAverage(snapshot, fill, filled);
        BigDecimal fee = snapshot.getCumulativeFee().add(fill.getFee());
        if (snapshot.getFeeAsset() != null && !snapshot.getFeeAsset().equals(fill.getFeeAsset())) {
            throw error("EXECUTION_ORDER_FEE_ASSET_CONFLICT", "feeAsset=" + fill.getFeeAsset());
        }
        List<ExecutionFill> fills = new ArrayList<>(snapshot.getFills());
        fills.add(fill);
        ExecutionOrderStatus status = quantityComparison == 0 ? ExecutionOrderStatus.FILLED : ExecutionOrderStatus.PARTIALLY_FILLED;
        Instant completedAt = quantityComparison == 0 ? fill.getFilledAt() : snapshot.getCompletedAt();
        return ExecutionOrderSnapshot.next(snapshot, status, snapshot.getExecutionOrderId(), filled, remaining, average, fee,
                fill.getFeeAsset(), fills, snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), fill.getFilledAt(), completedAt,
                null, null);
    }

    public ExecutionOrderSnapshot cancel(ExecutionOrderSnapshot snapshot, Instant canceledAt) {
        require(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        requireTime(snapshot, canceledAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.CANCELED, snapshot.getExecutionOrderId(), canceledAt, canceledAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), null, null);
    }

    public ExecutionOrderSnapshot reject(ExecutionOrderSnapshot snapshot, String errorCode, String errorMessage, Instant rejectedAt) {
        require(snapshot, ExecutionOrderStatus.CREATED, ExecutionOrderStatus.ACCEPTED);
        requireError(errorCode, errorMessage);
        requireTime(snapshot, rejectedAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.REJECTED, snapshot.getExecutionOrderId(), rejectedAt, rejectedAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), errorCode, errorMessage);
    }

    public ExecutionOrderSnapshot fail(ExecutionOrderSnapshot snapshot, String errorCode, String errorMessage, Instant failedAt) {
        require(snapshot, ExecutionOrderStatus.ACCEPTED, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        requireError(errorCode, errorMessage);
        requireTime(snapshot, failedAt, lastEvent(snapshot));
        return transition(snapshot, ExecutionOrderStatus.FAILED, snapshot.getExecutionOrderId(), failedAt, failedAt,
                snapshot.getAcceptedAt(), snapshot.getSubmittedAt(), errorCode, errorMessage);
    }

    private ExecutionOrderSnapshot transition(ExecutionOrderSnapshot old, ExecutionOrderStatus status, String executionOrderId,
                                               Instant eventAt, Instant completedAt, Instant acceptedAt, Instant submittedAt,
                                               String errorCode, String errorMessage) {
        return ExecutionOrderSnapshot.next(old, status, executionOrderId, old.getFilledQuantity(), old.getRemainingQuantity(),
                old.getAveragePrice(), old.getCumulativeFee(), old.getFeeAsset(), old.getFills(), acceptedAt,
                submittedAt, eventAt, completedAt, errorCode, errorMessage);
    }

    private BigDecimal weightedAverage(ExecutionOrderSnapshot snapshot, ExecutionFill fill, BigDecimal totalQuantity) {
        BigDecimal notional = fill.getQuantity().multiply(fill.getPrice());
        if (snapshot.getFilledQuantity().signum() > 0) {
            notional = notional.add(snapshot.getAveragePrice().multiply(snapshot.getFilledQuantity()));
        }
        return notional.divide(totalQuantity);
    }

    private Instant lastEvent(ExecutionOrderSnapshot snapshot) {
        if (!snapshot.getFills().isEmpty()) return snapshot.getFills().get(snapshot.getFills().size() - 1).getFilledAt();
        if (snapshot.getSubmittedAt() != null) return snapshot.getSubmittedAt();
        if (snapshot.getAcceptedAt() != null) return snapshot.getAcceptedAt();
        return snapshot.getRequest().getRequestedAt();
    }

    private void require(ExecutionOrderSnapshot snapshot, ExecutionOrderStatus... allowed) {
        if (snapshot == null) throw error("EXECUTION_ORDER_TRANSITION_INVALID", "snapshot is null");
        for (ExecutionOrderStatus status : allowed) if (snapshot.getStatus() == status) return;
        throw error("EXECUTION_ORDER_TRANSITION_INVALID", "status=" + snapshot.getStatus());
    }

    private void requireTime(ExecutionOrderSnapshot snapshot, Instant actual, Instant previous) {
        if (actual == null || previous == null || actual.isBefore(previous)) {
            throw error("EXECUTION_ORDER_TIME_INVALID", "event time moved backwards");
        }
    }

    private void requireError(String code, String message) {
        if (blank(code) || blank(message)) throw error("EXECUTION_ORDER_REQUEST_INVALID", "error code and message are required");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ExecutionOrderException error(String code, String message) { return new ExecutionOrderException(code, message); }
}
