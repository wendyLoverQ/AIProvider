package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable raw persisted fields used to restore one strategy signal decision. */
public final class StrategySignalDecisionRestoreRequest {
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
    private final BigDecimal signalPrice;
    private final StrategySignalDecisionReason reason;

    public StrategySignalDecisionRestoreRequest(
            String strategyCode,
            String strategyVersion,
            Map<String, Integer> strategyParameters,
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            KlineInterval interval,
            StrategyRuntimePosition currentPosition,
            StrategySignalType signalType,
            int signalCandleIndex,
            Instant signalOpenTime,
            Instant signalCloseTime,
            BigDecimal signalPrice,
            StrategySignalDecisionReason reason) {
        this.strategyCode = strategyCode;
        this.strategyVersion = strategyVersion;
        this.strategyParameters = immutableCopy(strategyParameters);
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.interval = interval;
        this.currentPosition = currentPosition;
        this.signalType = signalType;
        this.signalCandleIndex = signalCandleIndex;
        this.signalOpenTime = signalOpenTime;
        this.signalCloseTime = signalCloseTime;
        this.signalPrice = signalPrice;
        this.reason = reason;
    }

    private static Map<String, Integer> immutableCopy(Map<String, Integer> values) {
        if (values == null) return null;
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
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
    public BigDecimal getSignalPrice() { return signalPrice; }
    public StrategySignalDecisionReason getReason() { return reason; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StrategySignalDecisionRestoreRequest that)) return false;
        return signalCandleIndex == that.signalCandleIndex
                && Objects.equals(strategyCode, that.strategyCode)
                && Objects.equals(strategyVersion, that.strategyVersion)
                && Objects.equals(strategyParameters, that.strategyParameters)
                && provider == that.provider && marketType == that.marketType
                && Objects.equals(symbol, that.symbol) && interval == that.interval
                && currentPosition == that.currentPosition && signalType == that.signalType
                && Objects.equals(signalOpenTime, that.signalOpenTime)
                && Objects.equals(signalCloseTime, that.signalCloseTime)
                && Objects.equals(signalPrice, that.signalPrice) && reason == that.reason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategyCode, strategyVersion, strategyParameters, provider, marketType,
                symbol, interval, currentPosition, signalType, signalCandleIndex, signalOpenTime,
                signalCloseTime, signalPrice, reason);
    }
}
