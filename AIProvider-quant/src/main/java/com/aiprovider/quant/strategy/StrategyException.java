package com.aiprovider.quant.strategy;

public class StrategyException extends RuntimeException {
    private final String errorCode;
    public StrategyException(String errorCode, String message) { super(message); this.errorCode = errorCode; }
    public String getErrorCode() { return errorCode; }
}
