package com.aiprovider.model.vo;

import java.time.OffsetDateTime;

public class CryptoMarketHealthVO {
    private final String provider;
    private final boolean available;
    private final long latencyMs;
    private final OffsetDateTime checkedAt;
    private final String version;
    private final int exchangeCount;

    public CryptoMarketHealthVO(String provider, boolean available, long latencyMs, OffsetDateTime checkedAt) {
        this(provider, available, latencyMs, checkedAt, null, 0);
    }

    public CryptoMarketHealthVO(String provider, boolean available, long latencyMs, OffsetDateTime checkedAt, String version, int exchangeCount) {
        this.provider = provider;
        this.available = available;
        this.latencyMs = latencyMs;
        this.checkedAt = checkedAt;
        this.version = version;
        this.exchangeCount = exchangeCount;
    }

    public String getProvider() { return provider; }
    public boolean isAvailable() { return available; }
    public long getLatencyMs() { return latencyMs; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }
    public String getVersion() { return version; }
    public int getExchangeCount() { return exchangeCount; }
}
