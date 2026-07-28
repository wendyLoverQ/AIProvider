package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable latest best bid and ask. */
public final class RuntimeTopOfBook {
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final Instant eventTime;
    private final BigDecimal bidPrice;
    private final BigDecimal bidQuantity;
    private final BigDecimal askPrice;
    private final BigDecimal askQuantity;

    public RuntimeTopOfBook(MarketProviderId provider, MarketType marketType, String symbol,
                            Instant eventTime, BigDecimal bidPrice, BigDecimal bidQuantity,
                            BigDecimal askPrice, BigDecimal askQuantity) {
        if (provider == null || marketType == null || symbol == null || symbol.isBlank()
                || eventTime == null || !positive(bidPrice) || !positive(bidQuantity)
                || !positive(askPrice) || !positive(askQuantity)) {
            throw invalid("book context, eventTime, prices and quantities are required and positive");
        }
        if (marketType != MarketType.USDM_PERPETUAL) throw invalid("only USDM_PERPETUAL is supported");
        if (bidPrice.compareTo(askPrice) > 0) throw invalid("bidPrice must not exceed askPrice");
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.eventTime = RuntimeClosedCandle.copy(eventTime);
        this.bidPrice = RuntimeClosedCandle.copy(bidPrice);
        this.bidQuantity = RuntimeClosedCandle.copy(bidQuantity);
        this.askPrice = RuntimeClosedCandle.copy(askPrice);
        this.askQuantity = RuntimeClosedCandle.copy(askQuantity);
    }

    public static RuntimeTopOfBook from(StreamBookTickerEvent event) {
        if (event == null) throw invalid("book ticker event is required");
        return new RuntimeTopOfBook(event.getProvider(), event.getMarketType(), event.getSymbol(),
                event.getEventTime(), event.getBidPrice(), event.getBidQuantity(),
                event.getAskPrice(), event.getAskQuantity());
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static RuntimeMarketStateException invalid(String message) {
        return new RuntimeMarketStateException(RuntimeMarketStateException.BOOK_INVALID, message);
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public Instant getEventTime() { return RuntimeClosedCandle.copy(eventTime); }
    public BigDecimal getBidPrice() { return RuntimeClosedCandle.copy(bidPrice); }
    public BigDecimal getBidQuantity() { return RuntimeClosedCandle.copy(bidQuantity); }
    public BigDecimal getAskPrice() { return RuntimeClosedCandle.copy(askPrice); }
    public BigDecimal getAskQuantity() { return RuntimeClosedCandle.copy(askQuantity); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeTopOfBook)) return false;
        RuntimeTopOfBook that = (RuntimeTopOfBook) other;
        return provider == that.provider && marketType == that.marketType && symbol.equals(that.symbol)
                && eventTime.equals(that.eventTime) && bidPrice.equals(that.bidPrice)
                && bidQuantity.equals(that.bidQuantity) && askPrice.equals(that.askPrice)
                && askQuantity.equals(that.askQuantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, eventTime, bidPrice, bidQuantity,
                askPrice, askQuantity);
    }
}
