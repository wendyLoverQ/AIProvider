package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/** Keeps Ta4j strategy construction behind the adapter boundary. */
public final class Ta4jStrategyFactory {
    private final StrategyRegistry registry;

    public Ta4jStrategyFactory() { this(new StrategyRegistry()); }
    public Ta4jStrategyFactory(StrategyRegistry registry) { this.registry = registry; }

    public Strategy create(String code, BarSeries series, com.aiprovider.quant.strategy.StrategyBuildResult build) {
        QuantStrategyDefinition definition = registry.get(code);
        if (!definition.version().equals(build.getVersion())) throw new StrategyException("BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED", "strategy=" + code);
        if ("EMA_CROSS_LONG_ONLY".equals(definition.code())) {
            int fast = build.getParameters().get("fastPeriod");
            int slow = build.getParameters().get("slowPeriod");
            ClosePriceIndicator close = new ClosePriceIndicator(series);
            EMAIndicator fastEma = new EMAIndicator(close, fast);
            EMAIndicator slowEma = new EMAIndicator(close, slow);
            return new BaseStrategy(definition.code(), new CrossedUpIndicatorRule(fastEma, slowEma),
                    new CrossedDownIndicatorRule(fastEma, slowEma), slow);
        }
        if ("RSI_MEAN_REVERSION_LONG_ONLY".equals(definition.code())) return createRsiMeanReversion(definition, series, build);
        if ("MACD_TREND_LONG_ONLY".equals(definition.code())) return createMacdTrend(definition, series, build);
        throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategy=" + code);
    }

    private Strategy createRsiMeanReversion(QuantStrategyDefinition definition, BarSeries series, com.aiprovider.quant.strategy.StrategyBuildResult build) {
        int period = build.getParameters().get("rsiPeriod");
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        return new BaseStrategy(definition.code(), new UnderIndicatorRule(rsi, series.numOf(build.getParameters().get("entryThreshold"))),
                new OverIndicatorRule(rsi, series.numOf(build.getParameters().get("exitThreshold"))), period);
    }

    private Strategy createMacdTrend(QuantStrategyDefinition definition, BarSeries series, com.aiprovider.quant.strategy.StrategyBuildResult build) {
        int fast = build.getParameters().get("fastPeriod");
        int slow = build.getParameters().get("slowPeriod");
        int signalPeriod = build.getParameters().get("signalPeriod");
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), fast, slow);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
        return new BaseStrategy(definition.code(), new CrossedUpIndicatorRule(macd, signal),
                new CrossedDownIndicatorRule(macd, signal), slow + signalPeriod);
    }
}
