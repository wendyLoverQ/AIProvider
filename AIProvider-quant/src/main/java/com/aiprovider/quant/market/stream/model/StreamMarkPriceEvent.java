package com.aiprovider.quant.market.stream.model;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WebSocket markPrice 流事件（标记价格、指数价格、资金费率）。
 *
 * 来源于 Binance &lt;symbol&gt;@markPrice@1s 流。
 */
public class StreamMarkPriceEvent {

    private MarketProviderId provider;
    private MarketType marketType;
    private String symbol;
    private Instant eventTime;
    private BigDecimal markPrice;
    private BigDecimal indexPrice;
    private BigDecimal estimatedSettlePrice;
    private BigDecimal lastFundingRate;
    private BigDecimal interestRate;
    private Instant nextFundingTime;

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

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
}
