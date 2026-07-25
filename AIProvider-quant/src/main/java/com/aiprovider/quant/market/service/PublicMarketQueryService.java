package com.aiprovider.quant.market.service;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 公共行情查询服务。
 *
 * 向上层提供统一入口，按提供方标识委托给具体适配器。不直接访问上游，不做缓存或重试，
 * 不吞异常。所有上游失败由适配器抛出，由上层异常处理器统一处理。
 */
@Service
public class PublicMarketQueryService {

    private final PublicMarketProviderRegistry registry;

    public PublicMarketQueryService(PublicMarketProviderRegistry registry) {
        this.registry = registry;
    }

    /** 返回已注册提供方的标识与市场类型列表。 */
    public List<ProviderInfo> providers() {
        return registry.providers().stream()
                .map(p -> new ProviderInfo(p.providerId(), p.marketType()))
                .toList();
    }

    /** 查询提供方健康状态。 */
    public PublicMarketHealth health(MarketProviderId providerId) {
        return registry.require(providerId).health();
    }

    /** 查询永续合约目录。 */
    public List<PerpetualContract> contracts(MarketProviderId providerId, String quoteAsset) {
        return registry.require(providerId).contracts(quoteAsset);
    }

    /** 查询行情快照。 */
    public MarketSnapshot snapshot(MarketProviderId providerId, String symbol) {
        return registry.require(providerId).snapshot(symbol);
    }

    /** 查询 K 线。 */
    public List<com.aiprovider.quant.market.model.MarketCandle> klines(
            MarketProviderId providerId, String symbol, KlineInterval interval, int limit) {
        return registry.require(providerId).klines(symbol, interval, limit);
    }

    /** 提供方摘要信息。 */
    public static final class ProviderInfo {
        private final MarketProviderId providerId;
        private final MarketType marketType;

        public ProviderInfo(MarketProviderId providerId, MarketType marketType) {
            this.providerId = providerId;
            this.marketType = marketType;
        }

        public MarketProviderId getProviderId() { return providerId; }
        public MarketType getMarketType() { return marketType; }
    }
}
