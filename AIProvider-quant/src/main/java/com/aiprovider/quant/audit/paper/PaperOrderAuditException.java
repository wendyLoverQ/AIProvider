package com.aiprovider.quant.audit.paper;

public final class PaperOrderAuditException extends RuntimeException {
    public static final String PAPER_ORDER_AUDIT_REQUEST_INVALID =
            "PAPER_ORDER_AUDIT_REQUEST_INVALID";
    public static final String PAPER_ORDER_AUDIT_CONTEXT_MISMATCH =
            "PAPER_ORDER_AUDIT_CONTEXT_MISMATCH";
    public static final String PAPER_ORDER_AUDIT_INITIAL_STATE_INCONSISTENT =
            "PAPER_ORDER_AUDIT_INITIAL_STATE_INCONSISTENT";
    public static final String PAPER_ORDER_AUDIT_DUPLICATE_ID =
            "PAPER_ORDER_AUDIT_DUPLICATE_ID";
    public static final String PAPER_ORDER_AUDIT_TRANSITION_INVALID =
            "PAPER_ORDER_AUDIT_TRANSITION_INVALID";
    public static final String PAPER_ORDER_AUDIT_FILL_HISTORY_CONFLICT =
            "PAPER_ORDER_AUDIT_FILL_HISTORY_CONFLICT";
    public static final String PAPER_ORDER_AUDIT_TIME_INVALID =
            "PAPER_ORDER_AUDIT_TIME_INVALID";
    public static final String PAPER_ORDER_AUDIT_RECONCILIATION_FAILED =
            "PAPER_ORDER_AUDIT_RECONCILIATION_FAILED";

    private final String errorCode;

    public PaperOrderAuditException(String errorCode, String message) {
        super(message(errorCode, message));
        this.errorCode = requireErrorCode(errorCode);
    }

    public PaperOrderAuditException(String errorCode, String message, Throwable cause) {
        super(message(errorCode, message), cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String message(String errorCode, String detail) {
        String code = requireErrorCode(errorCode);
        return code + ": " + (detail == null ? "" : detail);
    }

    private static String requireErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        return errorCode;
    }
}
