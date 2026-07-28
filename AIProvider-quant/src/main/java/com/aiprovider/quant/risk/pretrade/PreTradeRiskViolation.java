package com.aiprovider.quant.risk.pretrade;

import java.math.BigDecimal;
import java.util.Objects;

public final class PreTradeRiskViolation {
    private final PreTradeRiskViolationCode code;
    private final BigDecimal actualValue;
    private final BigDecimal limitValue;
    private final String message;

    public PreTradeRiskViolation(
            PreTradeRiskViolationCode code,
            BigDecimal actualValue,
            BigDecimal limitValue,
            String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.actualValue = Objects.requireNonNull(actualValue, "actualValue");
        this.limitValue = Objects.requireNonNull(limitValue, "limitValue");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        this.message = message;
    }

    public PreTradeRiskViolationCode getCode() { return code; }
    public BigDecimal getActualValue() { return actualValue; }
    public BigDecimal getLimitValue() { return limitValue; }
    public String getMessage() { return message; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PreTradeRiskViolation that)) return false;
        return code == that.code
                && Objects.equals(actualValue, that.actualValue)
                && Objects.equals(limitValue, that.limitValue)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, actualValue, limitValue, message);
    }
}
