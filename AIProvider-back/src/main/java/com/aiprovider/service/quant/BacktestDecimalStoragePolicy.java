package com.aiprovider.service.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BacktestDecimalStoragePolicy {
    private BacktestDecimalStoragePolicy() {}
    public static BigDecimal normalizeRequired(BigDecimal value, String field) {
        if (value == null) throw invalid(field + " is null");
        return normalize(value, field);
    }
    public static BigDecimal normalizeNullable(BigDecimal value, String field) {
        return value == null ? null : normalize(value, field);
    }
    private static BigDecimal normalize(BigDecimal value, String field) {
        BigDecimal scaled = value.scale() > 18 ? value.setScale(18, RoundingMode.HALF_UP) : value;
        BigDecimal normalized = scaled.stripTrailingZeros();
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        if (integerDigits > 20 || scaled.precision() > 38) throw invalid(field + " exceeds DECIMAL(38,18)");
        return scaled;
    }
    private static BacktestTaskException invalid(String message) { return new BacktestTaskException("BACKTEST_RESULT_INVALID", message); }
}
