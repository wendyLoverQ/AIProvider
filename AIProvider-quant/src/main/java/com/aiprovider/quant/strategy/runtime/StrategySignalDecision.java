package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;

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
        this(strategyCode, strategyVersion, strategyParameters, provider, marketType, symbol, interval,
                currentPosition, signalType, signalCandleIndex, requireCandle(candle).getOpenTime(),
                candle.getCloseTime(), candle.getClosePrice(), reason);
    }

    private StrategySignalDecision(
            String strategyCode, String strategyVersion, Map<String, Integer> strategyParameters,
            MarketProviderId provider, MarketType marketType, String symbol, KlineInterval interval,
            StrategyRuntimePosition currentPosition, StrategySignalType signalType, int signalCandleIndex,
            Instant signalOpenTime, Instant signalCloseTime, BigDecimal signalPrice,
            StrategySignalDecisionReason reason) {
        validate(strategyCode, strategyVersion, strategyParameters, provider, marketType, symbol, interval,
                currentPosition, signalType, signalCandleIndex, signalOpenTime, signalCloseTime,
                signalPrice, reason);
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
        this.signalOpenTime = signalOpenTime;
        this.signalCloseTime = signalCloseTime;
        this.signalPrice = signalPrice;
        this.reason = reason;
    }

    private static HistoricalCandle requireCandle(HistoricalCandle candle) {
        if (candle == null) {
            throw invalid("candle is required");
        }
        return candle;
    }

    public static StrategySignalDecision restore(StrategySignalDecisionRestoreRequest request) {
        if (request == null) {
            throw invalid("request is null");
        }
        return new StrategySignalDecision(
                request.getStrategyCode(), request.getStrategyVersion(), request.getStrategyParameters(),
                request.getProvider(), request.getMarketType(), request.getSymbol(), request.getInterval(),
                request.getCurrentPosition(), request.getSignalType(), request.getSignalCandleIndex(),
                request.getSignalOpenTime(), request.getSignalCloseTime(), request.getSignalPrice(),
                request.getReason());
    }

    private static void validate(
            String strategyCode, String strategyVersion, Map<String, Integer> strategyParameters,
            MarketProviderId provider, MarketType marketType, String symbol, KlineInterval interval,
            StrategyRuntimePosition currentPosition, StrategySignalType signalType, int signalCandleIndex,
            Instant signalOpenTime, Instant signalCloseTime, BigDecimal signalPrice,
            StrategySignalDecisionReason reason) {
        if (blank(strategyCode) || blank(strategyVersion) || strategyParameters == null
                || provider == null || marketType == null || blank(symbol) || interval == null
                || currentPosition == null || signalType == null || signalCandleIndex < 0
                || signalOpenTime == null || signalCloseTime == null || signalPrice == null
                || reason == null) {
            throw invalid("required decision field is missing or invalid");
        }
        for (Map.Entry<String, Integer> entry : strategyParameters.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw invalid("strategyParameters must not contain null keys or values");
            }
        }
        if (!signalCloseTime.isAfter(signalOpenTime)) {
            throw invalid("signalCloseTime must be after signalOpenTime");
        }
        if (signalPrice.signum() <= 0) {
            throw invalid("signalPrice must be greater than zero");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static StrategySignalException invalid(String message) {
        return new StrategySignalException(
                StrategySignalException.STRATEGY_SIGNAL_RESTORE_INVALID, message);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StrategySignalDecision that)) return false;
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
