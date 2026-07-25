package com.aiprovider.quant.indicator;

public class IndicatorException extends RuntimeException {
    private final String errorCode;
    public IndicatorException(String errorCode, String message) { super(message); this.errorCode = errorCode; }
    public String getErrorCode() { return errorCode; }
}
