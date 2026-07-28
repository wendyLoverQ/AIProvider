package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class StrategySignalDecision {
    private final String strategyCode;
    private final String strategyVersion;
    private final Map<String, Integer> strategyParameters;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;
    private final StrategyRuntimePosition currentPosition;
    private final StrategySignalType signalType;
    private final int signalCandleIndex;
    private final Instant signalOpenTime;
    private final Instant signalCloseTime;
    /** Last closed candle close price; strategy reference only, never a guaranteed execution price. */
    private final BigDecimal signalPrice;
    private final StrategySignalDecisionReason reason;

    public StrategySignalDecision(String strategyCode, String strategyVersion, Map<String, Integer> strategyParameters,
                                  MarketProviderId provider, MarketType marketType, String symbol,
                                  KlineInterval interval, StrategyRuntimePosition currentPosition,
                                  StrategySignalType signalType, int signalCandleIndex, HistoricalCandle candle,
                                  StrategySignalDecisionReason reason) {
        this.strategyCode = strategyCode;
        this.strategyVersion = strategyVersion;
        this.strategyParameters = Map.copyOf(strategyParameters);
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.currentPosition = currentPosition;
        this.signalType = signalType;
        this.signalCandleIndex = signalCandleIndex;
        this.signalOpenTime = candle.getOpenTime();
        this.signalCloseTime = candle.getCloseTime();
        this.signalPrice = candle.getClosePrice();
        this.reason = reason;
    }

    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public Map<String, Integer> getStrategyParameters() { return strategyParameters; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public KlineInterval getInterval() { return interval; }
    public StrategyRuntimePosition getCurrentPosition() { return currentPosition; }
    public StrategySignalType getSignalType() { return signalType; }
    public int getSignalCandleIndex() { return signalCandleIndex; }
    public Instant getSignalOpenTime() { return signalOpenTime; }
    public Instant getSignalCloseTime() { return signalCloseTime; }
    /** Returns the strategy reference price, not an actual fill price. */
    public BigDecimal getSignalPrice() { return signalPrice; }
    public StrategySignalDecisionReason getReason() { return reason; }
}
