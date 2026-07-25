package com.aiprovider.quant.market.stream.model;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket ticker 流事件（最新成交价与 24h 统计）。
 *
 * 来源于 Binance &lt;symbol&gt;@ticker 流。
 */
public class StreamTickerEvent {

    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private Instant eventTime;
    private BigDecimal lastPrice;
    private BigDecimal priceChange;
    private BigDecimal priceChangePercent;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal volume;
    private BigDecimal quoteVolume;

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }

    public BigDecimal getPriceChange() { return priceChange; }
    public void setPriceChange(BigDecimal priceChange) { this.priceChange = priceChange; }

    public BigDecimal getPriceChangePercent() { return priceChangePercent; }
    public void setPriceChangePercent(BigDecimal priceChangePercent) { this.priceChangePercent = priceChangePercent; }

    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }

    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public BigDecimal getQuoteVolume() { return quoteVolume; }
    public void setQuoteVolume(BigDecimal quoteVolume) { this.quoteVolume = quoteVolume; }
}
