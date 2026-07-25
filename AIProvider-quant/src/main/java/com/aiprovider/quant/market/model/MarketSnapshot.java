package com.aiprovider.quant.market.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单个合约的公共行情快照。
 *
 * 由一次 snapshot 调用并行聚合 Binance 4 个公共端点（24hr ticker、premiumIndex、
 * bookTicker、openInterest）的结果。所有价格与数量使用 BigDecimal。
 * 任一上游端点失败时整体失败，不返回部分填充的快照。
 */
public class MarketSnapshot {

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
    private BigDecimal markPrice;
    private BigDecimal indexPrice;
    private BigDecimal estimatedSettlePrice;
    private BigDecimal lastFundingRate;
    private BigDecimal interestRate;
    private Instant nextFundingTime;
    private BigDecimal bidPrice;
    private BigDecimal bidQuantity;
    private BigDecimal askPrice;
    private BigDecimal askQuantity;
    private BigDecimal spread;
    private BigDecimal spreadRate;
    private BigDecimal openInterest;

    public MarketSnapshot() {}

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

    public BigDecimal getMarkPrice() { return markPrice; }
    public void setMarkPrice(BigDecimal markPrice) { this.markPrice = markPrice; }

    public BigDecimal getIndexPrice() { return indexPrice; }
    public void setIndexPrice(BigDecimal indexPrice) { this.indexPrice = indexPrice; }

    public BigDecimal getEstimatedSettlePrice() { return estimatedSettlePrice; }
    public void setEstimatedSettlePrice(BigDecimal estimatedSettlePrice) { this.estimatedSettlePrice = estimatedSettlePrice; }

    public BigDecimal getLastFundingRate() { return lastFundingRate; }
    public void setLastFundingRate(BigDecimal lastFundingRate) { this.lastFundingRate = lastFundingRate; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public Instant getNextFundingTime() { return nextFundingTime; }
    public void setNextFundingTime(Instant nextFundingTime) { this.nextFundingTime = nextFundingTime; }

    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }

    public BigDecimal getBidQuantity() { return bidQuantity; }
    public void setBidQuantity(BigDecimal bidQuantity) { this.bidQuantity = bidQuantity; }

    public BigDecimal getAskPrice() { return askPrice; }
    public void setAskPrice(BigDecimal askPrice) { this.askPrice = askPrice; }

    public BigDecimal getAskQuantity() { return askQuantity; }
    public void setAskQuantity(BigDecimal askQuantity) { this.askQuantity = askQuantity; }

    public BigDecimal getSpread() { return spread; }
    public void setSpread(BigDecimal spread) { this.spread = spread; }

    public BigDecimal getSpreadRate() { return spreadRate; }
    public void setSpreadRate(BigDecimal spreadRate) { this.spreadRate = spreadRate; }

    public BigDecimal getOpenInterest() { return openInterest; }
    public void setOpenInterest(BigDecimal openInterest) { this.openInterest = openInterest; }
}
