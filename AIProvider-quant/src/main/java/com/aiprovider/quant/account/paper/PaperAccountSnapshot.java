package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PaperAccountSnapshot {
    private final String accountId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String quoteAsset;
    private final BigDecimal initialCapital;
    private final BigDecimal realizedPnl;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal totalEquity;
    private final BigDecimal availableCapital;
    private final PaperPositionSnapshot position;
    private final PaperTradingDayState tradingDayState;
    private final int consecutiveLosses;
    private final List<PaperAppliedFill> appliedFills;
    private final Instant lastUpdatedAt;

    PaperAccountSnapshot(
            String accountId,
            MarketProviderId provider,
            MarketType marketType,
            String quoteAsset,
            BigDecimal initialCapital,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalEquity,
            BigDecimal availableCapital,
            PaperPositionSnapshot position,
            PaperTradingDayState tradingDayState,
            int consecutiveLosses,
            List<PaperAppliedFill> appliedFills,
            Instant lastUpdatedAt) {
        this.accountId = accountId;
        this.provider = provider;
        this.marketType = marketType;
        this.quoteAsset = quoteAsset;
        this.initialCapital = initialCapital;
        this.realizedPnl = realizedPnl;
        this.unrealizedPnl = unrealizedPnl;
        this.totalEquity = totalEquity;
        this.availableCapital = availableCapital;
        this.position = position;
        this.tradingDayState = tradingDayState;
        this.consecutiveLosses = consecutiveLosses;
        this.appliedFills = List.copyOf(appliedFills);
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getAccountId() { return accountId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getQuoteAsset() { return quoteAsset; }
    public BigDecimal getInitialCapital() { return initialCapital; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public BigDecimal getTotalEquity() { return totalEquity; }
    public BigDecimal getAvailableCapital() { return availableCapital; }
    public PaperPositionSnapshot getPosition() { return position; }
    public PaperTradingDayState getTradingDayState() { return tradingDayState; }
    public BigDecimal getDailyPnl() {
        return totalEquity.subtract(tradingDayState.getDayStartEquity());
    }
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public List<PaperAppliedFill> getAppliedFills() { return appliedFills; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperAccountSnapshot that)) return false;
        return consecutiveLosses == that.consecutiveLosses
                && Objects.equals(accountId, that.accountId)
                && provider == that.provider
                && marketType == that.marketType
                && Objects.equals(quoteAsset, that.quoteAsset)
                && Objects.equals(initialCapital, that.initialCapital)
                && Objects.equals(realizedPnl, that.realizedPnl)
                && Objects.equals(unrealizedPnl, that.unrealizedPnl)
                && Objects.equals(totalEquity, that.totalEquity)
                && Objects.equals(availableCapital, that.availableCapital)
                && Objects.equals(position, that.position)
                && Objects.equals(tradingDayState, that.tradingDayState)
                && Objects.equals(appliedFills, that.appliedFills)
                && Objects.equals(lastUpdatedAt, that.lastUpdatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                accountId, provider, marketType, quoteAsset, initialCapital, realizedPnl,
                unrealizedPnl, totalEquity, availableCapital, position, tradingDayState,
                consecutiveLosses, appliedFills, lastUpdatedAt);
    }
}
