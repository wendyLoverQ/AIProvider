package com.aiprovider.config.quant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binance USDⓈ-M Futures 公共行情配置。
 *
 * 对应 application.yml 中 {@code quant.binance-usdm} 前缀。
 * base-url 默认指向 Binance 官方 fapi 域名，可由环境变量覆盖。
 */
@ConfigurationProperties(prefix = "quant.binance-usdm")
public class BinanceUsdmMarketProperties {

    /** Binance fapi 基础地址。 */
    private String baseUrl = "https://fapi.binance.com";
    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 3000;
    /** 请求超时（毫秒）。 */
    private int requestTimeoutMs = 10000;
    /** 合约目录缓存 TTL（秒）。 */
    private int contractCacheSeconds = 300;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }

    public int getContractCacheSeconds() { return contractCacheSeconds; }
    public void setContractCacheSeconds(int contractCacheSeconds) { this.contractCacheSeconds = contractCacheSeconds; }
}
