package com.aiprovider.config.quant;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmMarketStreamClient;
import com.aiprovider.quant.market.stream.port.MarketStreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binance USDⓈ-M Futures 实时行情流 Spring 配置。
 *
 * 创建 {@link BinanceUsdmMarketStreamClient} Bean 并注册为 {@link MarketStreamClient}。
 * 由 {@link com.aiprovider.config.quant.QuantMarketWebSocketHandler} 注入使用。
 * 不创建独立服务或进程，不引入额外 Web 依赖。
 */
@Configuration
@EnableConfigurationProperties(BinanceUsdmStreamProperties.class)
public class BinanceUsdmStreamConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BinanceUsdmStreamConfiguration.class);

    @Bean
    public MarketStreamClient binanceUsdmMarketStreamClient(BinanceUsdmStreamProperties properties) {
        log.info("operation=stream-config-init wsBaseUrl={} connectTimeoutMs={} maxReconnectAttempts={} initialDelayMs={} maxDelayMs={}",
                properties.getWsBaseUrl(), properties.getConnectTimeoutMs(),
                properties.getMaxReconnectAttempts(), properties.getInitialReconnectDelayMs(),
                properties.getMaxReconnectDelayMs());
        return new BinanceUsdmMarketStreamClient(
                properties.getWsBaseUrl(),
                properties.getConnectTimeoutMs(),
                properties.getMaxReconnectAttempts(),
                properties.getInitialReconnectDelayMs(),
                properties.getMaxReconnectDelayMs());
    }
}
