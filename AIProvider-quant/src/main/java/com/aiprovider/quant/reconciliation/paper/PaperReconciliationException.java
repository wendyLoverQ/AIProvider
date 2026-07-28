package com.aiprovider.quant.reconciliation.paper;

public final class PaperReconciliationException extends RuntimeException {
    public static final String PAPER_RECONCILIATION_REQUEST_INVALID =
            "PAPER_RECONCILIATION_REQUEST_INVALID";
    public static final String PAPER_RECONCILIATION_CALCULATION_FAILED =
            "PAPER_RECONCILIATION_CALCULATION_FAILED";

    private final String errorCode;

    public PaperReconciliationException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = requireErrorCode(errorCode);
    }

    public PaperReconciliationException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String requireErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        return errorCode;
    }
}
