package com.aiprovider.quant.indicator;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.ta4j.Ta4jIndicatorEngine;

import java.util.List;

/** Public provider-neutral indicator facade. */
public final class IndicatorEngine {
    private final IndicatorCalculator calculator;

    public IndicatorEngine() { this(new Ta4jIndicatorEngine()); }
    public IndicatorEngine(IndicatorCalculator calculator) { this.calculator = calculator; }

    public IndicatorResult calculate(String seriesName, KlineInterval interval, List<HistoricalCandle> candles,
                                     IndicatorRequest request) {
        return calculator.calculate(seriesName, interval, candles, request);
    }
}
