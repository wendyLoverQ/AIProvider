package com.aiprovider.quant.market.runtime;

/** Stable failure contract for deterministic runtime market state processing. */
public final class RuntimeMarketStateException extends RuntimeException {
    public static final String REQUEST_INVALID = "RUNTIME_MARKET_REQUEST_INVALID";
    public static final String KEY_INVALID = "RUNTIME_MARKET_KEY_INVALID";
    public static final String CONTEXT_MISMATCH = "RUNTIME_MARKET_CONTEXT_MISMATCH";
    public static final String INTERVAL_NOT_SUPPORTED = "RUNTIME_MARKET_INTERVAL_NOT_SUPPORTED";
    public static final String CANDLE_INVALID = "RUNTIME_MARKET_CANDLE_INVALID";
    public static final String CANDLE_UNSORTED = "RUNTIME_MARKET_CANDLE_UNSORTED";
    public static final String CANDLE_GAP = "RUNTIME_MARKET_CANDLE_GAP";
    public static final String CANDLE_CONFLICT = "RUNTIME_MARKET_CANDLE_CONFLICT";
    public static final String BOOK_INVALID = "RUNTIME_MARKET_BOOK_INVALID";
    public static final String BOOK_CONFLICT = "RUNTIME_MARKET_BOOK_CONFLICT";
    public static final String EVENT_TIME_INVALID = "RUNTIME_MARKET_EVENT_TIME_INVALID";
    public static final String STATE_INVALID = "RUNTIME_MARKET_STATE_INVALID";
    public static final String RESTORE_INVALID = "RUNTIME_MARKET_RESTORE_INVALID";

    private final String errorCode;

    public RuntimeMarketStateException(String errorCode, String message) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public RuntimeMarketStateException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
