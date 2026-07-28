package com.aiprovider.quant.execution.simulation;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable best-bid/best-ask snapshot used by deterministic execution simulation.
 */
public final class SimulatedTopOfBook {
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final Instant eventTime;
    private final BigDecimal bidPrice;
    private final BigDecimal bidQuantity;
    private final BigDecimal askPrice;
    private final BigDecimal askQuantity;

    public SimulatedTopOfBook(MarketProviderId provider, MarketType marketType, String symbol, Instant eventTime,
                              BigDecimal bidPrice, BigDecimal bidQuantity,
                              BigDecimal askPrice, BigDecimal askQuantity) {
        if (provider == null || blank(symbol) || eventTime == null) {
            throw error("SIMULATED_EXECUTION_MARKET_INVALID", "provider, symbol and eventTime are required");
        }
        if (marketType != MarketType.USDM_PERPETUAL) {
            throw error("SIMULATED_EXECUTION_MARKET_INVALID", "only USDM_PERPETUAL is supported");
        }
        if (bidPrice == null || bidPrice.signum() <= 0 || askPrice == null || askPrice.signum() <= 0
                || bidPrice.compareTo(askPrice) > 0) {
            throw error("SIMULATED_EXECUTION_PRICE_INVALID", "bid and ask prices must be positive and non-crossed");
        }
        if (bidQuantity == null || bidQuantity.signum() <= 0
                || askQuantity == null || askQuantity.signum() <= 0) {
            throw error("SIMULATED_EXECUTION_LIQUIDITY_INVALID", "bid and ask quantities must be positive");
        }
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.eventTime = Instant.ofEpochSecond(eventTime.getEpochSecond(), eventTime.getNano());
        this.bidPrice = new BigDecimal(bidPrice.toString());
        this.bidQuantity = new BigDecimal(bidQuantity.toString());
        this.askPrice = new BigDecimal(askPrice.toString());
        this.askQuantity = new BigDecimal(askQuantity.toString());
    }

    public static SimulatedTopOfBook from(StreamBookTickerEvent event) {
        if (event == null) {
            throw error("SIMULATED_EXECUTION_REQUEST_INVALID", "book ticker event is null");
        }
        return new SimulatedTopOfBook(event.getProvider(), event.getMarketType(), event.getSymbol(),
                event.getEventTime(), event.getBidPrice(), event.getBidQuantity(),
                event.getAskPrice(), event.getAskQuantity());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static SimulatedExecutionException error(String code, String message) {
        return new SimulatedExecutionException(code, message);
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public Instant getEventTime() { return eventTime; }
    public BigDecimal getBidPrice() { return bidPrice; }
    public BigDecimal getBidQuantity() { return bidQuantity; }
    public BigDecimal getAskPrice() { return askPrice; }
    public BigDecimal getAskQuantity() { return askQuantity; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SimulatedTopOfBook that)) return false;
        return provider == that.provider && marketType == that.marketType && Objects.equals(symbol, that.symbol)
                && Objects.equals(eventTime, that.eventTime) && Objects.equals(bidPrice, that.bidPrice)
                && Objects.equals(bidQuantity, that.bidQuantity) && Objects.equals(askPrice, that.askPrice)
                && Objects.equals(askQuantity, that.askQuantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, eventTime, bidPrice, bidQuantity, askPrice, askQuantity);
    }
}
