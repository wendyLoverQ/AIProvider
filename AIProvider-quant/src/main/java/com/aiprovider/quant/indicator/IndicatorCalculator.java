package com.aiprovider.quant.indicator;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;

import java.util.List;

/** Provider-neutral indicator calculation port. */
public interface IndicatorCalculator {
    IndicatorResult calculate(String seriesName, KlineInterval interval, List<HistoricalCandle> candles,
                              IndicatorRequest request);
}
