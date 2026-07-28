package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.util.List;
import java.util.Map;

public final class StrategySignalRequest {
    private final String strategyCode;
    private final String strategyVersion;
    private final Map<String, Integer> strategyParameters;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;
    private final List<HistoricalCandle> candles;
    private final StrategyRuntimePosition currentPosition;

    public StrategySignalRequest(String strategyCode, String strategyVersion, Map<String, Integer> strategyParameters,
                                 MarketProviderId provider, MarketType marketType, String symbol,
                                 KlineInterval interval, List<HistoricalCandle> candles,
                                 StrategyRuntimePosition currentPosition) {
        this.strategyCode = strategyCode;
        this.strategyVersion = strategyVersion;
        this.strategyParameters = strategyParameters == null ? null : Map.copyOf(strategyParameters);
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.candles = candles == null ? null : List.copyOf(candles);
        this.currentPosition = currentPosition;
    }

    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public Map<String, Integer> getStrategyParameters() { return strategyParameters; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public List<HistoricalCandle> getCandles() { return candles; }
    public StrategyRuntimePosition getCurrentPosition() { return currentPosition; }
}
