package com.aiprovider.quant.strategy.runtime;

public final class StrategySignalException extends RuntimeException {
    public static final String STRATEGY_SIGNAL_RESTORE_INVALID = "STRATEGY_SIGNAL_RESTORE_INVALID";
    private final String errorCode;

    public StrategySignalException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = errorCode;
    }

    public StrategySignalException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
