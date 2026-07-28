package com.aiprovider.quant.strategy.runtime;

public final class StrategySignalException extends RuntimeException {
    private final String errorCode;

    public StrategySignalException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
