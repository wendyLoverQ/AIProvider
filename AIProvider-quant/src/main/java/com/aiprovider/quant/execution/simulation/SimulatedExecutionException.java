package com.aiprovider.quant.execution.simulation;

public final class SimulatedExecutionException extends RuntimeException {
    private final String errorCode;

    public SimulatedExecutionException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
