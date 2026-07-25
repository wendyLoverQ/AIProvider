package com.aiprovider.config.quant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binance USDⓈ-M Futures 实时行情流配置。
 *
 * 对应 application.yml 中 {@code quant.binance-usdm-stream} 前缀。
 * ws-base-url 默认指向 Binance 官方 fstream WebSocket 域名，可由环境变量覆盖。
 */
@ConfigurationProperties(prefix = "quant.binance-usdm-stream")
public class BinanceUsdmStreamProperties {

    /** Binance fstream WebSocket 基础地址。 */
    private String wsBaseUrl = "wss://fstream.binance.com";
    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 5000;
    /** 最大重连次数。 */
    private int maxReconnectAttempts = 6;
    /** 初始重连延迟（毫秒）。 */
    private long initialReconnectDelayMs = 1000;
    /** 最大重连延迟（毫秒）。 */
    private long maxReconnectDelayMs = 15000;

    public String getWsBaseUrl() { return wsBaseUrl; }
    public void setWsBaseUrl(String wsBaseUrl) { this.wsBaseUrl = wsBaseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

    public long getInitialReconnectDelayMs() { return initialReconnectDelayMs; }
    public void setInitialReconnectDelayMs(long initialReconnectDelayMs) { this.initialReconnectDelayMs = initialReconnectDelayMs; }

    public long getMaxReconnectDelayMs() { return maxReconnectDelayMs; }
    public void setMaxReconnectDelayMs(long maxReconnectDelayMs) { this.maxReconnectDelayMs = maxReconnectDelayMs; }
}
