package com.aiprovider.quant.market.stream.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;

import java.time.Instant;

/**
 * WebSocket 流状态变更事件。
 *
 * 当上游连接状态变化时发送给前端，前端据此取消"实时"标识。
 */
public class StreamStatusEvent {

    private MarketProviderId provider;
    private String symbol;
    private KlineInterval interval;
    private StreamStatus status;
    private String message;
    private Instant timestamp;

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public StreamStatus getStatus() { return status; }
    public void setStatus(StreamStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
