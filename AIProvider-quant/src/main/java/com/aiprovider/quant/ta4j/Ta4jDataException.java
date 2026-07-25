package com.aiprovider.quant.ta4j;

public class Ta4jDataException extends RuntimeException {
    private final String errorCode;

    public Ta4jDataException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
