package com.aiprovider.quant.runtime.paper;

/** Stable failure contract for realtime paper-runtime orchestration. */
public final class PaperRuntimeException extends RuntimeException {
    public static final String PAPER_RUNTIME_REQUEST_INVALID = "PAPER_RUNTIME_REQUEST_INVALID";
    public static final String PAPER_RUNTIME_CONFIG_INVALID = "PAPER_RUNTIME_CONFIG_INVALID";
    public static final String PAPER_RUNTIME_CONTEXT_MISMATCH = "PAPER_RUNTIME_CONTEXT_MISMATCH";
    public static final String PAPER_RUNTIME_EVENT_TIME_INVALID = "PAPER_RUNTIME_EVENT_TIME_INVALID";
    public static final String PAPER_RUNTIME_EVENT_DATE_INVALID = "PAPER_RUNTIME_EVENT_DATE_INVALID";
    public static final String PAPER_RUNTIME_MARKET_FAILED = "PAPER_RUNTIME_MARKET_FAILED";
    public static final String PAPER_RUNTIME_TRADING_FAILED = "PAPER_RUNTIME_TRADING_FAILED";
    public static final String PAPER_RUNTIME_ACCOUNT_FAILED = "PAPER_RUNTIME_ACCOUNT_FAILED";
    public static final String PAPER_RUNTIME_STATE_INVALID = "PAPER_RUNTIME_STATE_INVALID";
    public static final String PAPER_RUNTIME_RESTORE_INVALID = "PAPER_RUNTIME_RESTORE_INVALID";
    public static final String PAPER_RUNTIME_RESTORE_CONTEXT_MISMATCH =
            "PAPER_RUNTIME_RESTORE_CONTEXT_MISMATCH";
    public static final String PAPER_RUNTIME_RESTORE_STEP_MISMATCH =
            "PAPER_RUNTIME_RESTORE_STEP_MISMATCH";

    private final String errorCode;

    public PaperRuntimeException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = requireCode(errorCode);
    }

    public PaperRuntimeException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.errorCode = requireCode(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String requireCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        return errorCode;
    }
}
