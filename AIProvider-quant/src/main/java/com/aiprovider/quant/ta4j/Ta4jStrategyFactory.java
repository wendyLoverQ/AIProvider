package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
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
        throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategy=" + code);
    }
}
