package com.aiprovider.quant.market.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单根 K 线（蜡烛）。
 *
 * 来源于 Binance /fapi/v1/klines。所有价格与数量使用 BigDecimal。
 * {@code closed} 字段根据上游 closeTime 与服务器时间判断该 K 线是否已闭合。
 */
public class MarketCandle {

    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private KlineInterval interval;
    private Instant openTime;
    private Instant closeTime;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private BigDecimal quoteVolume;
    private long tradeCount;
    private BigDecimal takerBuyBaseVolume;
    private BigDecimal takerBuyQuoteVolume;
    private boolean closed;

    public MarketCandle() {}

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public Instant getOpenTime() { return openTime; }
    public void setOpenTime(Instant openTime) { this.openTime = openTime; }

    public Instant getCloseTime() { return closeTime; }
    public void setCloseTime(Instant closeTime) { this.closeTime = closeTime; }

    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }

    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }

    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }

    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public BigDecimal getQuoteVolume() { return quoteVolume; }
    public void setQuoteVolume(BigDecimal quoteVolume) { this.quoteVolume = quoteVolume; }

    public long getTradeCount() { return tradeCount; }
    public void setTradeCount(long tradeCount) { this.tradeCount = tradeCount; }

    public BigDecimal getTakerBuyBaseVolume() { return takerBuyBaseVolume; }
    public void setTakerBuyBaseVolume(BigDecimal takerBuyBaseVolume) { this.takerBuyBaseVolume = takerBuyBaseVolume; }

    public BigDecimal getTakerBuyQuoteVolume() { return takerBuyQuoteVolume; }
    public void setTakerBuyQuoteVolume(BigDecimal takerBuyQuoteVolume) { this.takerBuyQuoteVolume = takerBuyQuoteVolume; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
