package com.aiprovider.quant.market.model;

import java.time.Instant;

/**
 * 公共行情提供方健康状态。
 *
 * 由一次真实上游探测填充，禁止伪造 available=true。当上游不可达时 available=false，
 * 并保留最近一次探测时间与延迟。
 */
public class PublicMarketHealth {

    /** 提供方标识。 */
    private MarketProviderId provider;
    /** 市场类型。 */
    private MarketType marketType;
    /** 上游是否可用。 */
    private boolean available;
    /** 上游服务器时间。 */
    private Instant serverTime;
    /** 本地探测时间。 */
    private Instant localTime;
    /** 上游时间与本地时间偏差（毫秒）。 */
    private long clockOffsetMs;
    /** 本次探测延迟（毫秒）。 */
    private long latencyMs;
    /** 探测完成时间。 */
    private Instant checkedAt;
    /** Binance X-MBX-USED-WEIGHT-1M 响应头值，可空。 */
    private String usedWeight1m;

    public PublicMarketHealth() {}

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Instant getServerTime() { return serverTime; }
    public void setServerTime(Instant serverTime) { this.serverTime = serverTime; }

    public Instant getLocalTime() { return localTime; }
    public void setLocalTime(Instant localTime) { this.localTime = localTime; }

    public long getClockOffsetMs() { return clockOffsetMs; }
    public void setClockOffsetMs(long clockOffsetMs) { this.clockOffsetMs = clockOffsetMs; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }

    public String getUsedWeight1m() { return usedWeight1m; }
    public void setUsedWeight1m(String usedWeight1m) { this.usedWeight1m = usedWeight1m; }
}
