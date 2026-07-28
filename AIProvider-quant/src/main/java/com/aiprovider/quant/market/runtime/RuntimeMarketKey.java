package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.util.Objects;

/** Immutable identity of one runtime market-state stream. */
public final class RuntimeMarketKey {
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;

    public RuntimeMarketKey(MarketProviderId provider, MarketType marketType, String symbol,
                            KlineInterval interval) {
        if (provider == null || marketType == null || symbol == null || symbol.isBlank() || interval == null) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.KEY_INVALID,
                    "provider, marketType, symbol and interval are required");
        }
        if (marketType != MarketType.USDM_PERPETUAL) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.KEY_INVALID,
                    "only USDM_PERPETUAL is supported");
        }
        if (!interval.isFixedDuration()) {
            throw new RuntimeMarketStateException(RuntimeMarketStateException.INTERVAL_NOT_SUPPORTED,
                    "runtime market state requires a fixed-duration interval: " + interval.code());
        }
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
    }

    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RuntimeMarketKey)) return false;
        RuntimeMarketKey that = (RuntimeMarketKey) other;
        return provider == that.provider && marketType == that.marketType
                && symbol.equals(that.symbol) && interval == that.interval;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, marketType, symbol, interval);
    }

    @Override
    public String toString() {
        return provider + ":" + marketType + ":" + symbol + ":" + interval.code();
    }
}
