package com.aiprovider.config.quant;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmPublicMarketClient;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binance USDⓈ-M Futures 公共行情 Spring 配置。
 *
 * 创建 {@link BinanceUsdmPublicMarketClient} Bean 并注册为 {@link PublicMarketDataProvider}，
 * 由 {@link com.aiprovider.quant.market.service.PublicMarketProviderRegistry} 自动收集。
 * 不创建独立服务或进程，不引入 Web 或数据库依赖。
 */
@Configuration
@EnableConfigurationProperties(BinanceUsdmMarketProperties.class)
public class BinanceUsdmMarketConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BinanceUsdmMarketConfiguration.class);

    @Bean
    public PublicMarketDataProvider binanceUsdmPublicMarketProvider(BinanceUsdmMarketProperties properties) {
        log.info("operation=binance-usdm-init baseUrl={} connectTimeoutMs={} requestTimeoutMs={} contractCacheSeconds={}",
                properties.getBaseUrl(), properties.getConnectTimeoutMs(),
                properties.getRequestTimeoutMs(), properties.getContractCacheSeconds());
        return new BinanceUsdmPublicMarketClient(
                properties.getBaseUrl(),
                properties.getConnectTimeoutMs(),
                properties.getRequestTimeoutMs(),
                properties.getContractCacheSeconds());
    }
}
