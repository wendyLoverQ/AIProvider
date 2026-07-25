package com.aiprovider.quant.market.service;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.port.PublicMarketDataProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 公共行情提供方注册表。
 *
 * 在 Spring 容器启动时收集所有 {@link PublicMarketDataProvider} Bean，按 {@link MarketProviderId}
 * 建立查找表。查找失败抛出 {@link IllegalArgumentException}，禁止返回 null 或默认值。
 */
public class PublicMarketProviderRegistry {

    private final Map<MarketProviderId, PublicMarketDataProvider> providers;

    public PublicMarketProviderRegistry(List<PublicMarketDataProvider> providers) {
        Objects.requireNonNull(providers, "providers 不能为空");
        Map<MarketProviderId, PublicMarketDataProvider> map = new LinkedHashMap<>();
        for (PublicMarketDataProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            MarketProviderId id = provider.providerId();
            if (id == null) {
                throw new IllegalStateException("公共行情提供方 providerId() 返回 null: " + provider.getClass().getName());
            }
            if (map.put(id, provider) != null) {
                throw new IllegalStateException("存在重复的公共行情提供方标识: " + id);
            }
        }
        this.providers = Collections.unmodifiableMap(map);
    }

    /**
     * 按提供方标识查找适配器。
     *
     * @param providerId 提供方标识
     * @return 适配器实例
     * @throws IllegalArgumentException 当 providerId 为 null 或未注册
     */
    public PublicMarketDataProvider require(MarketProviderId providerId) {
        if (providerId == null) {
            throw new IllegalArgumentException("行情提供方标识不能为空");
        }
        PublicMarketDataProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("未注册的行情提供方: " + providerId);
        }
        return provider;
    }

    /** 返回已注册提供方列表（不可变）。 */
    public List<PublicMarketDataProvider> providers() {
        return List.copyOf(providers.values());
    }
}
