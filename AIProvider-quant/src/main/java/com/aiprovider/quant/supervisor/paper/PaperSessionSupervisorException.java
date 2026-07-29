package com.aiprovider.quant.supervisor.paper;

public final class PaperSessionSupervisorException extends RuntimeException {
    public static final String PAPER_SUPERVISOR_REQUEST_INVALID = "PAPER_SUPERVISOR_REQUEST_INVALID";
    public static final String PAPER_SUPERVISOR_CONTEXT_MISMATCH = "PAPER_SUPERVISOR_CONTEXT_MISMATCH";
    public static final String PAPER_SUPERVISOR_STATE_INVALID = "PAPER_SUPERVISOR_STATE_INVALID";
    public static final String PAPER_SUPERVISOR_INITIAL_STATE_INCONSISTENT =
            "PAPER_SUPERVISOR_INITIAL_STATE_INCONSISTENT";
    public static final String PAPER_SUPERVISOR_STREAM_SUBSCRIBE_FAILED =
            "PAPER_SUPERVISOR_STREAM_SUBSCRIBE_FAILED";
    public static final String PAPER_SUPERVISOR_STREAM_UNSUBSCRIBE_FAILED =
            "PAPER_SUPERVISOR_STREAM_UNSUBSCRIBE_FAILED";
    public static final String PAPER_SUPERVISOR_RUNTIME_FAILED = "PAPER_SUPERVISOR_RUNTIME_FAILED";
    public static final String PAPER_SUPERVISOR_AUDIT_FAILED = "PAPER_SUPERVISOR_AUDIT_FAILED";
    public static final String PAPER_SUPERVISOR_RECONCILIATION_INCONSISTENT =
            "PAPER_SUPERVISOR_RECONCILIATION_INCONSISTENT";
    public static final String PAPER_SUPERVISOR_STREAM_FAILED = "PAPER_SUPERVISOR_STREAM_FAILED";

    public PaperSessionSupervisorException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = requireCode(errorCode);
    }

    public PaperSessionSupervisorException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.errorCode = requireCode(errorCode);
    }

    private final String errorCode;

    public String getErrorCode() {
        return errorCode;
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        return value;
    }
}
