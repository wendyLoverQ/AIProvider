package com.aiprovider.service.quant;

public class ResearchStudyTaskException extends RuntimeException {
  private final String errorCode;
  public ResearchStudyTaskException(String errorCode, String message) { super(message); this.errorCode = errorCode; }
  public String getErrorCode() { return errorCode; }
}
