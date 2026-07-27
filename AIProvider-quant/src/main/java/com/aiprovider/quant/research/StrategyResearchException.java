package com.aiprovider.quant.research;

public final class StrategyResearchException extends RuntimeException {
    private final String errorCode;

    public StrategyResearchException(String errorCode, String message) {
        super(message == null ? "" : message.trim());
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
