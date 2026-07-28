package com.aiprovider.quant.risk.pretrade;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;

public final class PreTradeRiskContext {
    private static final String INVALID = "PRE_TRADE_RISK_CONTEXT_INVALID";

    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final BigDecimal referencePrice;
    private final BigDecimal feeRate;
    private final BigDecimal totalEquity;
    private final BigDecimal availableCapital;
    private final BigDecimal currentPositionQuantity;
    private final BigDecimal currentPositionNotional;
    private final BigDecimal dayStartEquity;
    private final BigDecimal dailyRealizedPnl;
    private final int consecutiveLosses;

    public PreTradeRiskContext(
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            BigDecimal referencePrice,
            BigDecimal feeRate,
            BigDecimal totalEquity,
            BigDecimal availableCapital,
            BigDecimal currentPositionQuantity,
            BigDecimal currentPositionNotional,
            BigDecimal dayStartEquity,
            BigDecimal dailyRealizedPnl,
            int consecutiveLosses) {
        if (provider == null || marketType == null || symbol == null || symbol.isBlank()) {
            throw invalid("provider, marketType and symbol are required");
        }
        if (marketType != MarketType.USDM_PERPETUAL) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_MARKET_NOT_SUPPORTED",
                    "only USDM_PERPETUAL is supported");
        }
        requirePositive(referencePrice, "referencePrice");
        requireNonNegative(feeRate, "feeRate");
        requirePositive(totalEquity, "totalEquity");
        requireNonNegative(availableCapital, "availableCapital");
        requireNonNegative(currentPositionQuantity, "currentPositionQuantity");
        requireNonNegative(currentPositionNotional, "currentPositionNotional");
        requirePositive(dayStartEquity, "dayStartEquity");
        if (dailyRealizedPnl == null) {
            throw invalid("dailyRealizedPnl is required");
        }
        if (consecutiveLosses < 0) {
            throw invalid("consecutiveLosses must not be negative");
        }
        if (availableCapital.compareTo(totalEquity) > 0) {
            throw invalid("availableCapital must not exceed totalEquity");
        }
        boolean quantityIsZero = currentPositionQuantity.signum() == 0;
        boolean notionalIsZero = currentPositionNotional.signum() == 0;
        if (quantityIsZero != notionalIsZero) {
            throw invalid("position quantity and notional must both be zero or both be positive");
        }
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.referencePrice = referencePrice;
        this.feeRate = feeRate;
        this.totalEquity = totalEquity;
        this.availableCapital = availableCapital;
        this.currentPositionQuantity = currentPositionQuantity;
        this.currentPositionNotional = currentPositionNotional;
        this.dayStartEquity = dayStartEquity;
        this.dailyRealizedPnl = dailyRealizedPnl;
        this.consecutiveLosses = consecutiveLosses;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw invalid(field + " must be greater than zero");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw invalid(field + " must not be negative");
        }
    }

    private static PreTradeRiskException invalid(String detail) {
        return new PreTradeRiskException(INVALID, detail);
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getFeeRate() { return feeRate; }
    public BigDecimal getTotalEquity() { return totalEquity; }
    public BigDecimal getAvailableCapital() { return availableCapital; }
    public BigDecimal getCurrentPositionQuantity() { return currentPositionQuantity; }
    public BigDecimal getCurrentPositionNotional() { return currentPositionNotional; }
    public BigDecimal getDayStartEquity() { return dayStartEquity; }
    public BigDecimal getDailyRealizedPnl() { return dailyRealizedPnl; }
    public int getConsecutiveLosses() { return consecutiveLosses; }
}
