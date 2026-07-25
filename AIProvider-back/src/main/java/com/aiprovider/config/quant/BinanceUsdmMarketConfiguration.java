package com.aiprovider.config.quant;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmPublicMarketClient;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;
import com.aiprovider.quant.market.service.PublicMarketProviderRegistry;
import com.aiprovider.quant.market.service.PublicMarketQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binance USDⓈ-M Futures 公共行情 Spring 配置。
 *
 * 创建 {@link BinanceUsdmPublicMarketClient} Bean。该类同时实现
 * {@link PublicMarketDataProvider} 和
 * {@link com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider}，
 * 由 {@link com.aiprovider.quant.market.service.PublicMarketProviderRegistry} 自动收集为公共行情提供方，
 * 由 {@link com.aiprovider.quant.market.history.service.MarketHistorySyncService} 注入为历史行情提供方。
 * 不创建独立服务或进程，不引入 Web 或数据库依赖。
 */
@Configuration
@EnableConfigurationProperties(BinanceUsdmMarketProperties.class)
public class BinanceUsdmMarketConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BinanceUsdmMarketConfiguration.class);

    @Bean
    public BinanceUsdmPublicMarketClient binanceUsdmPublicMarketClient(BinanceUsdmMarketProperties properties) {
        log.info("operation=binance-usdm-init baseUrl={} connectTimeoutMs={} requestTimeoutMs={} contractCacheSeconds={}",
                properties.getBaseUrl(), properties.getConnectTimeoutMs(),
                properties.getRequestTimeoutMs(), properties.getContractCacheSeconds());
        return new BinanceUsdmPublicMarketClient(
                properties.getBaseUrl(),
                properties.getConnectTimeoutMs(),
                properties.getRequestTimeoutMs(),
                properties.getContractCacheSeconds());
    }

    @Bean
    public PublicMarketProviderRegistry publicMarketProviderRegistry(List<PublicMarketDataProvider> providers) {
        log.info("operation=public-market-registry-init providerCount={}", providers.size());
        return new PublicMarketProviderRegistry(providers);
    }

    @Bean
    public PublicMarketQueryService publicMarketQueryService(PublicMarketProviderRegistry registry) {
        log.info("operation=public-market-query-service-init");
        return new PublicMarketQueryService(registry);
    }
}
