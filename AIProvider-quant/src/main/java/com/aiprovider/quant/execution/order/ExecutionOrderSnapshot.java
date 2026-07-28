package com.aiprovider.quant.execution.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ExecutionOrderSnapshot {
    private final ExecutionOrderRequest request;
    private final ExecutionOrderStatus status;
    private final String executionOrderId;
    private final BigDecimal filledQuantity;
    private final BigDecimal remainingQuantity;
    private final BigDecimal averagePrice;
    private final BigDecimal cumulativeFee;
    private final String feeAsset;
    private final List<ExecutionFill> fills;
    private final Instant acceptedAt;
    private final Instant submittedAt;
    private final Instant lastUpdatedAt;
    private final Instant completedAt;
    private final String terminalErrorCode;
    private final String terminalErrorMessage;

    private ExecutionOrderSnapshot(ExecutionOrderRequest request, ExecutionOrderStatus status, String executionOrderId,
                                   BigDecimal filledQuantity, BigDecimal remainingQuantity, BigDecimal averagePrice,
                                   BigDecimal cumulativeFee, String feeAsset, List<ExecutionFill> fills,
                                   Instant acceptedAt, Instant submittedAt, Instant lastUpdatedAt, Instant completedAt,
                                   String terminalErrorCode, String terminalErrorMessage) {
        this.request = request;
        this.status = status;
        this.executionOrderId = executionOrderId;
        this.filledQuantity = filledQuantity;
        this.remainingQuantity = remainingQuantity;
        this.averagePrice = averagePrice;
        this.cumulativeFee = cumulativeFee;
        this.feeAsset = feeAsset;
        this.fills = List.copyOf(fills);
        this.acceptedAt = acceptedAt;
        this.submittedAt = submittedAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.completedAt = completedAt;
        this.terminalErrorCode = terminalErrorCode;
        this.terminalErrorMessage = terminalErrorMessage;
    }

    static ExecutionOrderSnapshot created(ExecutionOrderRequest request) {
        return new ExecutionOrderSnapshot(request, ExecutionOrderStatus.CREATED, null, BigDecimal.ZERO,
                request.getQuantity(), null, BigDecimal.ZERO, null, List.of(), null, null,
                request.getRequestedAt(), null, null, null);
    }

    static ExecutionOrderSnapshot next(ExecutionOrderSnapshot old, ExecutionOrderStatus status, String executionOrderId,
                                       BigDecimal filledQuantity, BigDecimal remainingQuantity, BigDecimal averagePrice,
                                       BigDecimal cumulativeFee, String feeAsset, List<ExecutionFill> fills,
                                       Instant acceptedAt, Instant submittedAt, Instant lastUpdatedAt, Instant completedAt,
                                       String errorCode, String errorMessage) {
        return new ExecutionOrderSnapshot(old.request, status, executionOrderId, filledQuantity, remainingQuantity,
                averagePrice, cumulativeFee, feeAsset, fills, acceptedAt, submittedAt, lastUpdatedAt, completedAt,
                errorCode, errorMessage);
    }

    public ExecutionOrderRequest getRequest() { return request; }
    public ExecutionOrderStatus getStatus() { return status; }
    public String getExecutionOrderId() { return executionOrderId; }
    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public BigDecimal getCumulativeFee() { return cumulativeFee; }
    public String getFeeAsset() { return feeAsset; }
    public List<ExecutionFill> getFills() { return fills; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getTerminalErrorCode() { return terminalErrorCode; }
    public String getTerminalErrorMessage() { return terminalErrorMessage; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExecutionOrderSnapshot that)) return false;
        return Objects.equals(request, that.request) && status == that.status
                && Objects.equals(executionOrderId, that.executionOrderId) && Objects.equals(filledQuantity, that.filledQuantity)
                && Objects.equals(remainingQuantity, that.remainingQuantity) && Objects.equals(averagePrice, that.averagePrice)
                && Objects.equals(cumulativeFee, that.cumulativeFee) && Objects.equals(feeAsset, that.feeAsset)
                && Objects.equals(fills, that.fills) && Objects.equals(acceptedAt, that.acceptedAt)
                && Objects.equals(submittedAt, that.submittedAt) && Objects.equals(lastUpdatedAt, that.lastUpdatedAt)
                && Objects.equals(completedAt, that.completedAt) && Objects.equals(terminalErrorCode, that.terminalErrorCode)
                && Objects.equals(terminalErrorMessage, that.terminalErrorMessage);
    }
    @Override public int hashCode() { return Objects.hash(request, status, executionOrderId, filledQuantity, remainingQuantity, averagePrice, cumulativeFee, feeAsset, fills, acceptedAt, submittedAt, lastUpdatedAt, completedAt, terminalErrorCode, terminalErrorMessage); }
}
