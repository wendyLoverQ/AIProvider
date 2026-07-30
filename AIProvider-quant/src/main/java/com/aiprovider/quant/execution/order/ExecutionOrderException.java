package com.aiprovider.quant.execution.order;

public final class ExecutionOrderException extends RuntimeException {
    private final String errorCode;

    public ExecutionOrderException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = errorCode;
    }

    public ExecutionOrderException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
