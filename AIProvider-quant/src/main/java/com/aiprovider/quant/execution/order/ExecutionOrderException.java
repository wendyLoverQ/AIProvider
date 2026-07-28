package com.aiprovider.quant.execution.order;

public final class ExecutionOrderException extends RuntimeException {
    private final String errorCode;

    public ExecutionOrderException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
