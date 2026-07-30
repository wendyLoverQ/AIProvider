package com.aiprovider.quant.engine.paper;

public final class PaperTradingException extends RuntimeException {
    public static final String PAPER_TRADING_REQUEST_INVALID = "PAPER_TRADING_REQUEST_INVALID";
    public static final String PAPER_TRADING_CONFIG_INVALID = "PAPER_TRADING_CONFIG_INVALID";
    public static final String PAPER_TRADING_CONTEXT_MISMATCH = "PAPER_TRADING_CONTEXT_MISMATCH";
    public static final String PAPER_TRADING_CANDLE_TIME_INVALID = "PAPER_TRADING_CANDLE_TIME_INVALID";
    public static final String PAPER_TRADING_CANDLE_CONFLICT = "PAPER_TRADING_CANDLE_CONFLICT";
    public static final String PAPER_TRADING_ORDER_NOT_PENDING = "PAPER_TRADING_ORDER_NOT_PENDING";
    public static final String PAPER_TRADING_SIGNAL_FAILED = "PAPER_TRADING_SIGNAL_FAILED";
    public static final String PAPER_TRADING_SIZING_FAILED = "PAPER_TRADING_SIZING_FAILED";
    public static final String PAPER_TRADING_RISK_FAILED = "PAPER_TRADING_RISK_FAILED";
    public static final String PAPER_TRADING_ORDER_FAILED = "PAPER_TRADING_ORDER_FAILED";
    public static final String PAPER_TRADING_EXECUTION_FAILED = "PAPER_TRADING_EXECUTION_FAILED";
    public static final String PAPER_TRADING_ACCOUNT_FAILED = "PAPER_TRADING_ACCOUNT_FAILED";
    public static final String PAPER_TRADING_STATE_INVALID = "PAPER_TRADING_STATE_INVALID";
    public static final String PAPER_TRADING_RESTORE_INVALID = "PAPER_TRADING_RESTORE_INVALID";
    public static final String PAPER_TRADING_RESTORE_CONTEXT_MISMATCH =
            "PAPER_TRADING_RESTORE_CONTEXT_MISMATCH";

    private final String errorCode;

    public PaperTradingException(String errorCode, String message) {
        super(message == null ? "" : message);
        this.errorCode = requireErrorCode(errorCode);
    }

    public PaperTradingException(String errorCode, String message, Throwable cause) {
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
