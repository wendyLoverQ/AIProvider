package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable persisted fields used to rehydrate a paper account snapshot. */
public final class PaperAccountRestoreRequest {
    private final String accountId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String quoteAsset;
    private final BigDecimal initialCapital;
    private final BigDecimal realizedPnl;
    private final BigDecimal unrealizedPnl;
    private final BigDecimal totalEquity;
    private final BigDecimal availableCapital;
    private final boolean positionOpen;
    private final String positionSymbol;
    private final BigDecimal positionQuantity;
    private final BigDecimal averageEntryPrice;
    private final BigDecimal markPrice;
    private final BigDecimal positionNotional;
    private final BigDecimal positionUnrealizedPnl;
    private final String openingClientOrderId;
    private final BigDecimal openTradeNetPnl;
    private final LocalDate tradingUtcDate;
    private final BigDecimal dayStartEquity;
    private final BigDecimal dailyRealizedPnl;
    private final int consecutiveLosses;
    private final List<PaperAppliedFill> appliedFills;
    private final Instant lastUpdatedAt;

    public PaperAccountRestoreRequest(
            String accountId,
            MarketProviderId provider,
            MarketType marketType,
            String quoteAsset,
            BigDecimal initialCapital,
            BigDecimal realizedPnl,
            BigDecimal unrealizedPnl,
            BigDecimal totalEquity,
            BigDecimal availableCapital,
            boolean positionOpen,
            String positionSymbol,
            BigDecimal positionQuantity,
            BigDecimal averageEntryPrice,
            BigDecimal markPrice,
            BigDecimal positionNotional,
            BigDecimal positionUnrealizedPnl,
            String openingClientOrderId,
            BigDecimal openTradeNetPnl,
            LocalDate tradingUtcDate,
            BigDecimal dayStartEquity,
            BigDecimal dailyRealizedPnl,
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
        this.positionOpen = positionOpen;
        this.positionSymbol = positionSymbol;
        this.positionQuantity = positionQuantity;
        this.averageEntryPrice = averageEntryPrice;
        this.markPrice = markPrice;
        this.positionNotional = positionNotional;
        this.positionUnrealizedPnl = positionUnrealizedPnl;
        this.openingClientOrderId = openingClientOrderId;
        this.openTradeNetPnl = openTradeNetPnl;
        this.tradingUtcDate = tradingUtcDate;
        this.dayStartEquity = dayStartEquity;
        this.dailyRealizedPnl = dailyRealizedPnl;
        this.consecutiveLosses = consecutiveLosses;
        this.appliedFills = immutableCopy(appliedFills);
        this.lastUpdatedAt = lastUpdatedAt;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null) return null;
        return Collections.unmodifiableList(new ArrayList<>(values));
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
    public boolean isPositionOpen() { return positionOpen; }
    public String getPositionSymbol() { return positionSymbol; }
    public BigDecimal getPositionQuantity() { return positionQuantity; }
    public BigDecimal getAverageEntryPrice() { return averageEntryPrice; }
    public BigDecimal getMarkPrice() { return markPrice; }
    public BigDecimal getPositionNotional() { return positionNotional; }
    public BigDecimal getPositionUnrealizedPnl() { return positionUnrealizedPnl; }
    public String getOpeningClientOrderId() { return openingClientOrderId; }
    public BigDecimal getOpenTradeNetPnl() { return openTradeNetPnl; }
    public LocalDate getTradingUtcDate() { return tradingUtcDate; }
    public BigDecimal getDayStartEquity() { return dayStartEquity; }
    public BigDecimal getDailyRealizedPnl() { return dailyRealizedPnl; }
    public int getConsecutiveLosses() { return consecutiveLosses; }
    public List<PaperAppliedFill> getAppliedFills() { return appliedFills; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperAccountRestoreRequest that)) return false;
        return positionOpen == that.positionOpen && consecutiveLosses == that.consecutiveLosses
                && Objects.equals(accountId, that.accountId) && provider == that.provider
                && marketType == that.marketType && Objects.equals(quoteAsset, that.quoteAsset)
                && Objects.equals(initialCapital, that.initialCapital)
                && Objects.equals(realizedPnl, that.realizedPnl)
                && Objects.equals(unrealizedPnl, that.unrealizedPnl)
                && Objects.equals(totalEquity, that.totalEquity)
                && Objects.equals(availableCapital, that.availableCapital)
                && Objects.equals(positionSymbol, that.positionSymbol)
                && Objects.equals(positionQuantity, that.positionQuantity)
                && Objects.equals(averageEntryPrice, that.averageEntryPrice)
                && Objects.equals(markPrice, that.markPrice)
                && Objects.equals(positionNotional, that.positionNotional)
                && Objects.equals(positionUnrealizedPnl, that.positionUnrealizedPnl)
                && Objects.equals(openingClientOrderId, that.openingClientOrderId)
                && Objects.equals(openTradeNetPnl, that.openTradeNetPnl)
                && Objects.equals(tradingUtcDate, that.tradingUtcDate)
                && Objects.equals(dayStartEquity, that.dayStartEquity)
                && Objects.equals(dailyRealizedPnl, that.dailyRealizedPnl)
                && Objects.equals(appliedFills, that.appliedFills)
                && Objects.equals(lastUpdatedAt, that.lastUpdatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, provider, marketType, quoteAsset, initialCapital, realizedPnl,
                unrealizedPnl, totalEquity, availableCapital, positionOpen, positionSymbol,
                positionQuantity, averageEntryPrice, markPrice, positionNotional,
                positionUnrealizedPnl, openingClientOrderId, openTradeNetPnl, tradingUtcDate,
                dayStartEquity, dailyRealizedPnl, consecutiveLosses, appliedFills, lastUpdatedAt);
    }
}
