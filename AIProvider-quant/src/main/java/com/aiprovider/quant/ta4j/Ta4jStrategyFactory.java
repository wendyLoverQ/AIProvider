package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.aiprovider.quant.strategy.EmaCrossLongOnlyDefinition;
import com.aiprovider.quant.strategy.RsiMeanReversionLongOnlyDefinition;
import com.aiprovider.quant.strategy.MacdTrendLongOnlyDefinition;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Rule;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.IsEqualRule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/** Keeps Ta4j strategy construction behind the adapter boundary. */
public final class Ta4jStrategyFactory {
    private final StrategyRegistry registry;

    public Ta4jStrategyFactory() { this(new StrategyRegistry()); }
    public Ta4jStrategyFactory(StrategyRegistry registry) { this.registry = registry; }

    public Strategy create(String code, BarSeries series, com.aiprovider.quant.strategy.StrategyBuildResult build) {
        QuantStrategyDefinition definition = registry.get(code);
        if (!definition.version().equals(build.getVersion())) throw new StrategyException("BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED", "strategy=" + code);
        if (EmaCrossLongOnlyDefinition.CODE.equals(definition.code())) {
            int fast = build.getParameters().get("fastPeriod");
            int slow = build.getParameters().get("slowPeriod");
            ClosePriceIndicator close = new ClosePriceIndicator(series);
            EMAIndicator fastEma = new EMAIndicator(close, fast);
            EMAIndicator slowEma = new EMAIndicator(close, slow);
            return new BaseStrategy(definition.code(), new CrossedUpIndicatorRule(fastEma, slowEma),
                    new CrossedDownIndicatorRule(fastEma, slowEma), slow);
        }
        if (RsiMeanReversionLongOnlyDefinition.CODE.equals(definition.code())) return createRsiMeanReversion(definition, series, build);
        if (MacdTrendLongOnlyDefinition.CODE.equals(definition.code())) return createMacdTrend(definition, series, build);
        throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategy=" + code);
    }

    private Strategy createRsiMeanReversion(QuantStrategyDefinition definition, BarSeries series, com.aiprovider.quant.strategy.StrategyBuildResult build) {
        int period = build.getParameters().get("rsiPeriod");
        RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(series), period);
        Num entryThreshold = series.numOf(build.getParameters().get("entryThreshold"));
        Num exitThreshold = series.numOf(build.getParameters().get("exitThreshold"));
        Rule entryRule = new UnderIndicatorRule(rsi, entryThreshold).or(new IsEqualRule(rsi, entryThreshold));
        Rule exitRule = new OverIndicatorRule(rsi, exitThreshold).or(new IsEqualRule(rsi, exitThreshold));
        return new BaseStrategy(definition.code(), entryRule, exitRule, period);
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
