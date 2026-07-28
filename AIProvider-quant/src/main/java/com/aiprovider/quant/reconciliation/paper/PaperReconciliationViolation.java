package com.aiprovider.quant.reconciliation.paper;

import java.util.Objects;

public final class PaperReconciliationViolation {
    private final PaperReconciliationViolationCode code;
    private final String message;
    private final String clientOrderId;
    private final String executionOrderId;
    private final String fillId;
    private final String expectedValue;
    private final String actualValue;

    public PaperReconciliationViolation(
            PaperReconciliationViolationCode code,
            String message,
            String clientOrderId,
            String executionOrderId,
            String fillId,
            String expectedValue,
            String actualValue) {
        if (code == null || message == null || message.isBlank()) {
            throw new PaperReconciliationException(
                    PaperReconciliationException.PAPER_RECONCILIATION_REQUEST_INVALID,
                    "Violation code and message are required");
        }
        this.code = code;
        this.message = message;
        this.clientOrderId = clientOrderId;
        this.executionOrderId = executionOrderId;
        this.fillId = fillId;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
    }

    public PaperReconciliationViolationCode getCode() { return code; }
    public String getMessage() { return message; }
    public String getClientOrderId() { return clientOrderId; }
    public String getExecutionOrderId() { return executionOrderId; }
    public String getFillId() { return fillId; }
    public String getExpectedValue() { return expectedValue; }
    public String getActualValue() { return actualValue; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperReconciliationViolation that)) return false;
        return code == that.code
                && Objects.equals(message, that.message)
                && Objects.equals(clientOrderId, that.clientOrderId)
                && Objects.equals(executionOrderId, that.executionOrderId)
                && Objects.equals(fillId, that.fillId)
                && Objects.equals(expectedValue, that.expectedValue)
                && Objects.equals(actualValue, that.actualValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                code, message, clientOrderId, executionOrderId, fillId, expectedValue, actualValue);
    }
}
