package com.aiprovider.quant.risk.pretrade;

import java.math.BigDecimal;

public final class PreTradeRiskPolicy {
    private static final String INVALID = "PRE_TRADE_RISK_POLICY_INVALID";

    private final BigDecimal maxOrderNotionalRatio;
    private final BigDecimal maxTotalExposureRatio;
    private final BigDecimal minimumRemainingCapitalRatio;
    private final BigDecimal maxDailyLossRatio;
    private final int maxConsecutiveLosses;

    public PreTradeRiskPolicy(
            BigDecimal maxOrderNotionalRatio,
            BigDecimal maxTotalExposureRatio,
            BigDecimal minimumRemainingCapitalRatio,
            BigDecimal maxDailyLossRatio,
            int maxConsecutiveLosses) {
        requireRatioAboveZeroAtMostOne(maxOrderNotionalRatio, "maxOrderNotionalRatio");
        requireRatioAboveZeroAtMostOne(maxTotalExposureRatio, "maxTotalExposureRatio");
        if (minimumRemainingCapitalRatio == null
                || minimumRemainingCapitalRatio.signum() < 0
                || minimumRemainingCapitalRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw invalid("minimumRemainingCapitalRatio must be at least zero and less than one");
        }
        requireRatioAboveZeroAtMostOne(maxDailyLossRatio, "maxDailyLossRatio");
        if (maxConsecutiveLosses <= 0) {
            throw invalid("maxConsecutiveLosses must be greater than zero");
        }
        this.maxOrderNotionalRatio = maxOrderNotionalRatio;
        this.maxTotalExposureRatio = maxTotalExposureRatio;
        this.minimumRemainingCapitalRatio = minimumRemainingCapitalRatio;
        this.maxDailyLossRatio = maxDailyLossRatio;
        this.maxConsecutiveLosses = maxConsecutiveLosses;
    }

    private static void requireRatioAboveZeroAtMostOne(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw invalid(field + " must be greater than zero and at most one");
        }
    }

    private static PreTradeRiskException invalid(String detail) {
        return new PreTradeRiskException(INVALID, detail);
    }

    public BigDecimal getMaxOrderNotionalRatio() { return maxOrderNotionalRatio; }
    public BigDecimal getMaxTotalExposureRatio() { return maxTotalExposureRatio; }
    public BigDecimal getMinimumRemainingCapitalRatio() { return minimumRemainingCapitalRatio; }
    public BigDecimal getMaxDailyLossRatio() { return maxDailyLossRatio; }
    public int getMaxConsecutiveLosses() { return maxConsecutiveLosses; }
}
