package com.aiprovider.service;

public class ContentAiException extends RuntimeException {
    private final String code;
    public ContentAiException(String code,String message){super(message);this.code=code;}
    public ContentAiException(String code,String message,Throwable cause){super(message,cause);this.code=code;}
    public String getCode(){return code;}
}
