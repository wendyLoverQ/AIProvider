package com.aiprovider.service.quant;

public class WalkForwardTaskException extends RuntimeException {
  private final String errorCode;

  public WalkForwardTaskException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
