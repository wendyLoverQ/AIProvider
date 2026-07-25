package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;

/**
 * 创建历史行情同步任务的请求。
 *
 * 前端提交后，后端归一化时间范围并创建 dataset/task。
 */
public class MarketSyncRequest {

    private MarketProviderId provider;
    private MarketType marketType;
    private MarketDataType dataType;
    private String symbol;
    private KlineInterval interval;
    private Instant startTime;
    private Instant endTime;

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public MarketDataType getDataType() { return dataType; }
    public void setDataType(MarketDataType dataType) { this.dataType = dataType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
}
