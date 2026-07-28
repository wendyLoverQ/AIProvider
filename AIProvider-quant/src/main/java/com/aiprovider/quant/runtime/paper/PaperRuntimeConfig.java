package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;

import java.util.Objects;

/** Immutable configuration joining one market stream to one paper-trading session. */
public final class PaperRuntimeConfig {
    private final RuntimeMarketKey marketKey;
    private final int maxClosedCandles;
    private final PaperTradingSessionConfig tradingConfig;

    public PaperRuntimeConfig(RuntimeMarketKey marketKey, int maxClosedCandles,
                              PaperTradingSessionConfig tradingConfig) {
        if (marketKey == null || tradingConfig == null || maxClosedCandles < 2) {
            throw invalid("marketKey and tradingConfig are required and maxClosedCandles must be at least 2");
        }
        if (marketKey.getProvider() != tradingConfig.getProvider()
                || marketKey.getMarketType() != tradingConfig.getMarketType()
                || !marketKey.getSymbol().equals(tradingConfig.getSymbol())
                || marketKey.getInterval() != tradingConfig.getKlineInterval()) {
            throw invalid("marketKey provider, marketType, symbol and interval must match tradingConfig");
        }
        this.marketKey = marketKey;
        this.maxClosedCandles = maxClosedCandles;
        this.tradingConfig = tradingConfig;
    }

    public RuntimeMarketKey getMarketKey() { return marketKey; }
    public int getMaxClosedCandles() { return maxClosedCandles; }
    public PaperTradingSessionConfig getTradingConfig() { return tradingConfig; }

    private static PaperRuntimeException invalid(String message) {
        return new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_CONFIG_INVALID, message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperRuntimeConfig that)) return false;
        return maxClosedCandles == that.maxClosedCandles
                && marketKey.equals(that.marketKey)
                && tradingConfig.equals(that.tradingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketKey, maxClosedCandles, tradingConfig);
    }
}
