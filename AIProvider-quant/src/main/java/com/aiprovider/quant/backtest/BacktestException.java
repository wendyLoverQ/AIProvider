package com.aiprovider.quant.backtest;

public class BacktestException extends RuntimeException {
    private final String errorCode;
    public BacktestException(String code, String message) { super(message); this.errorCode = code; }
    public BacktestException(String code, String message, Throwable cause) { super(message, cause); this.errorCode = code; }
    public String getErrorCode() { return errorCode; }
}
