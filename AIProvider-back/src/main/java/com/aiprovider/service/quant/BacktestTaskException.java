package com.aiprovider.service.quant;

public class BacktestTaskException extends RuntimeException {
    private final String errorCode;
    public BacktestTaskException(String errorCode, String message) { super(message); this.errorCode = errorCode; }
    public BacktestTaskException(String errorCode, String message, Throwable cause) { super(message, cause); this.errorCode = errorCode; }
    public String getErrorCode() { return errorCode; }
}
