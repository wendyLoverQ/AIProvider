package com.aiprovider.quant.market.stream.model;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket bookTicker 流事件（最佳买卖价）。
 *
 * 来源于 Binance &lt;symbol&gt;@bookTicker 流。
 */
public class StreamBookTickerEvent {

    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private Instant eventTime;
    private BigDecimal bidPrice;
    private BigDecimal bidQuantity;
    private BigDecimal askPrice;
    private BigDecimal askQuantity;

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }

    public BigDecimal getBidQuantity() { return bidQuantity; }
    public void setBidQuantity(BigDecimal bidQuantity) { this.bidQuantity = bidQuantity; }

    public BigDecimal getAskPrice() { return askPrice; }
    public void setAskPrice(BigDecimal askPrice) { this.askPrice = askPrice; }

    public BigDecimal getAskQuantity() { return askQuantity; }
    public void setAskQuantity(BigDecimal askQuantity) { this.askQuantity = askQuantity; }
}
