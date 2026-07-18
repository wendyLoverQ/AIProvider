package com.aiprovider.service;

public class FoundryUnavailableException extends RuntimeException {
    public FoundryUnavailableException(String message) { super(message); }
    public FoundryUnavailableException(String message, Throwable cause) { super(message, cause); }
}
