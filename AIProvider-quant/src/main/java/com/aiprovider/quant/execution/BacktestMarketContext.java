package com.aiprovider.quant.execution;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketType;
import java.util.Set;

public record BacktestMarketContext(
        String provider,
        MarketType marketType,
        String dataType,
        String symbol,
        KlineInterval interval,
        Set<MarketFeature> availableFeatures) {

    public BacktestMarketContext {
        availableFeatures = availableFeatures == null ? Set.of() : Set.copyOf(availableFeatures);
    }
}
