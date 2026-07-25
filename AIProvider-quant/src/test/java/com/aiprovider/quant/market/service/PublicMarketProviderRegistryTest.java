package com.aiprovider.quant.market.service;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketSnapshot;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.model.PublicMarketHealth;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PublicMarketProviderRegistry} 单元测试。
 *
 * 验证：注册的提供方可查找、未注册的提供方抛 IllegalArgumentException、null 标识抛异常、
 * 重复标识抛 IllegalStateException。
 */
class PublicMarketProviderRegistryTest {

    @Test
    void registeredProviderFoundByProviderId() {
        PublicMarketDataProvider binance = new StubProvider(MarketProviderId.BINANCE_USDM);
        PublicMarketProviderRegistry registry = new PublicMarketProviderRegistry(List.of(binance));

        assertThat(registry.require(MarketProviderId.BINANCE_USDM)).isSameAs(binance);
        assertThat(registry.providers()).hasSize(1).contains(binance);
    }

    @Test
    void unknownProviderThrowsIllegalArgumentException() {
        PublicMarketProviderRegistry registry = new PublicMarketProviderRegistry(Collections.emptyList());

        assertThatThrownBy(() -> registry.require(MarketProviderId.BINANCE_USDM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册的行情提供方");
    }

    @Test
    void nullProviderIdThrowsIllegalArgumentException() {
        PublicMarketProviderRegistry registry = new PublicMarketProviderRegistry(Collections.emptyList());

        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void duplicateProviderIdThrowsIllegalStateException() {
        PublicMarketDataProvider a = new StubProvider(MarketProviderId.BINANCE_USDM);
        PublicMarketDataProvider b = new StubProvider(MarketProviderId.BINANCE_USDM);

        assertThatThrownBy(() -> new PublicMarketProviderRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    /** 简单测试桩实现。 */
    private static final class StubProvider implements PublicMarketDataProvider {
        private final MarketProviderId id;

        StubProvider(MarketProviderId id) {
            this.id = id;
        }

        @Override
        public MarketProviderId providerId() { return id; }

        @Override
        public MarketType marketType() { return MarketType.USDM_PERPETUAL; }

        @Override
        public PublicMarketHealth health() { return null; }

        @Override
        public List<PerpetualContract> contracts(String quoteAsset) { return Collections.emptyList(); }

        @Override
        public MarketSnapshot snapshot(String symbol) { return null; }

        @Override
        public List<MarketCandle> klines(String symbol, KlineInterval interval, int limit) { return Collections.emptyList(); }
    }
}
