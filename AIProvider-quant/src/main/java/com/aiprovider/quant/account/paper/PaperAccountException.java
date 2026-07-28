package com.aiprovider.quant.account.paper;

public final class PaperAccountException extends RuntimeException {
    public static final String PAPER_ACCOUNT_REQUEST_INVALID = "PAPER_ACCOUNT_REQUEST_INVALID";
    public static final String PAPER_ACCOUNT_MARKET_NOT_SUPPORTED = "PAPER_ACCOUNT_MARKET_NOT_SUPPORTED";
    public static final String PAPER_ACCOUNT_CONTEXT_MISMATCH = "PAPER_ACCOUNT_CONTEXT_MISMATCH";
    public static final String PAPER_ACCOUNT_FEE_ASSET_MISMATCH = "PAPER_ACCOUNT_FEE_ASSET_MISMATCH";
    public static final String PAPER_ACCOUNT_POSITION_ALREADY_OPEN = "PAPER_ACCOUNT_POSITION_ALREADY_OPEN";
    public static final String PAPER_ACCOUNT_POSITION_NOT_OPEN = "PAPER_ACCOUNT_POSITION_NOT_OPEN";
    public static final String PAPER_ACCOUNT_EXIT_QUANTITY_EXCEEDED = "PAPER_ACCOUNT_EXIT_QUANTITY_EXCEEDED";
    public static final String PAPER_ACCOUNT_CAPITAL_INSUFFICIENT = "PAPER_ACCOUNT_CAPITAL_INSUFFICIENT";
    public static final String PAPER_ACCOUNT_DUPLICATE_FILL_CONFLICT = "PAPER_ACCOUNT_DUPLICATE_FILL_CONFLICT";
    public static final String PAPER_ACCOUNT_TIME_INVALID = "PAPER_ACCOUNT_TIME_INVALID";
    public static final String PAPER_ACCOUNT_CALCULATION_FAILED = "PAPER_ACCOUNT_CALCULATION_FAILED";

    private final String errorCode;

    public PaperAccountException(String errorCode, String message) {
        super(message == null ? "" : message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public PaperAccountException(String errorCode, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
