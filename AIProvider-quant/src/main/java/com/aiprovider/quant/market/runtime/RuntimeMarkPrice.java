package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable latest mark-price state detached from its mutable stream event. */
public final class RuntimeMarkPrice {
    static final String MARK_PRICE_INVALID = "RUNTIME_MARKET_MARK_PRICE_INVALID";

    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final Instant eventTime;
    private final BigDecimal markPrice;
    private final BigDecimal indexPrice;
    private final BigDecimal estimatedSettlePrice;
    private final BigDecimal lastFundingRate;
    private final BigDecimal interestRate;
    private final Instant nextFundingTime;

    public RuntimeMarkPrice(
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            Instant eventTime,
            BigDecimal markPrice,
            BigDecimal indexPrice,
            BigDecimal estimatedSettlePrice,
            BigDecimal lastFundingRate,
            BigDecimal interestRate,
            Instant nextFundingTime) {
        if (provider == null || marketType == null || symbol == null || symbol.isBlank()
                || eventTime == null || markPrice == null || markPrice.signum() <= 0) {
            throw invalid("mark price context, eventTime and positive markPrice are required");
        }
        if (negative(indexPrice) || negative(estimatedSettlePrice)) {
            throw invalid("optional price fields must not be negative");
        }
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.eventTime = RuntimeClosedCandle.copy(eventTime);
        this.markPrice = RuntimeClosedCandle.copy(markPrice);
        this.indexPrice = RuntimeClosedCandle.copy(indexPrice);
        this.estimatedSettlePrice = RuntimeClosedCandle.copy(estimatedSettlePrice);
        this.lastFundingRate = RuntimeClosedCandle.copy(lastFundingRate);
        this.interestRate = RuntimeClosedCandle.copy(interestRate);
        this.nextFundingTime = RuntimeClosedCandle.copy(nextFundingTime);
    }

    public static RuntimeMarkPrice from(StreamMarkPriceEvent event) {
        if (event == null) {
            throw invalid("mark price event is required");
        }
        return new RuntimeMarkPrice(
                event.getProvider(),
                event.getMarketType(),
                event.getSymbol(),
                event.getEventTime(),
                event.getMarkPrice(),
                event.getIndexPrice(),
                event.getEstimatedSettlePrice(),
                event.getLastFundingRate(),
                event.getInterestRate(),
                event.getNextFundingTime());
    }

    private static boolean negative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private static RuntimeMarketStateException invalid(String message) {
        return new RuntimeMarketStateException(MARK_PRICE_INVALID, message);
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public Instant getEventTime() { return RuntimeClosedCandle.copy(eventTime); }
    public BigDecimal getMarkPrice() { return RuntimeClosedCandle.copy(markPrice); }
    public BigDecimal getIndexPrice() { return RuntimeClosedCandle.copy(indexPrice); }
    public BigDecimal getEstimatedSettlePrice() {
        return RuntimeClosedCandle.copy(estimatedSettlePrice);
    }
    public BigDecimal getLastFundingRate() {
        return RuntimeClosedCandle.copy(lastFundingRate);
    }
    public BigDecimal getInterestRate() { return RuntimeClosedCandle.copy(interestRate); }
    public Instant getNextFundingTime() { return RuntimeClosedCandle.copy(nextFundingTime); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeMarkPrice)) return false;
        RuntimeMarkPrice that = (RuntimeMarkPrice) other;
        return provider == that.provider
                && marketType == that.marketType
                && symbol.equals(that.symbol)
                && eventTime.equals(that.eventTime)
                && markPrice.equals(that.markPrice)
                && Objects.equals(indexPrice, that.indexPrice)
                && Objects.equals(estimatedSettlePrice, that.estimatedSettlePrice)
                && Objects.equals(lastFundingRate, that.lastFundingRate)
                && Objects.equals(interestRate, that.interestRate)
                && Objects.equals(nextFundingTime, that.nextFundingTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, eventTime, markPrice, indexPrice,
                estimatedSettlePrice, lastFundingRate, interestRate, nextFundingTime);
    }
}
