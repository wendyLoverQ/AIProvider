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

import java.util.Map;

/** Keeps Ta4j strategy construction behind the adapter boundary. */
public final class Ta4jStrategyFactory {
    private final StrategyRegistry registry;

    public Ta4jStrategyFactory() { this(new StrategyRegistry()); }
    public Ta4jStrategyFactory(StrategyRegistry registry) { this.registry = registry; }

    public Strategy create(String code, BarSeries series, Map<String, Integer> parameters) {
        QuantStrategyDefinition definition = registry.get(code);
        definition.build(parameters, series.getBarCount());
        if ("EMA_CROSS_LONG_ONLY".equals(definition.code())) {
            int fast = parameters.get("fastPeriod");
            int slow = parameters.get("slowPeriod");
            ClosePriceIndicator close = new ClosePriceIndicator(series);
            EMAIndicator fastEma = new EMAIndicator(close, fast);
            EMAIndicator slowEma = new EMAIndicator(close, slow);
            return new BaseStrategy(definition.code(), new CrossedUpIndicatorRule(fastEma, slowEma),
                    new CrossedDownIndicatorRule(fastEma, slowEma), slow);
        }
        throw new StrategyException("BACKTEST_STRATEGY_NOT_FOUND", "strategy=" + code);
    }
}
