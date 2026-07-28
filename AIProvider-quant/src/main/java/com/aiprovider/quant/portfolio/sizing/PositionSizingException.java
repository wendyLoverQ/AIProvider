package com.aiprovider.quant.portfolio.sizing;

public class PositionSizingException extends RuntimeException {
    public static final String POSITION_SIZING_REQUEST_INVALID =
            "POSITION_SIZING_REQUEST_INVALID";
    public static final String POSITION_SIZING_POLICY_INVALID =
            "POSITION_SIZING_POLICY_INVALID";
    public static final String POSITION_SIZING_CONTRACT_RULES_INVALID =
            "POSITION_SIZING_CONTRACT_RULES_INVALID";
    public static final String POSITION_SIZING_MARKET_NOT_SUPPORTED =
            "POSITION_SIZING_MARKET_NOT_SUPPORTED";
    public static final String POSITION_SIZING_LEVERAGE_NOT_SUPPORTED =
            "POSITION_SIZING_LEVERAGE_NOT_SUPPORTED";
    public static final String POSITION_SIZING_QUANTITY_NORMALIZATION_ZERO =
            "POSITION_SIZING_QUANTITY_NORMALIZATION_ZERO";
    public static final String POSITION_SIZING_QUANTITY_BELOW_MINIMUM =
            "POSITION_SIZING_QUANTITY_BELOW_MINIMUM";
    public static final String POSITION_SIZING_QUANTITY_ABOVE_MAXIMUM =
            "POSITION_SIZING_QUANTITY_ABOVE_MAXIMUM";
    public static final String POSITION_SIZING_NOTIONAL_BELOW_MINIMUM =
            "POSITION_SIZING_NOTIONAL_BELOW_MINIMUM";
    public static final String POSITION_SIZING_CAPITAL_INSUFFICIENT =
            "POSITION_SIZING_CAPITAL_INSUFFICIENT";
    public static final String POSITION_SIZING_CALCULATION_FAILED =
            "POSITION_SIZING_CALCULATION_FAILED";

    private final String errorCode;

    public PositionSizingException(String errorCode, String message) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
    }

    public PositionSizingException(String errorCode, String message, Throwable cause) {
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
