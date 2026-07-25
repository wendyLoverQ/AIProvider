package com.aiprovider.quant.indicator;

import java.util.List;
import java.util.Map;

public final class IndicatorResult {
    private final IndicatorType indicatorType;
    private final Map<String, Integer> parameters;
    private final int unstableBars;
    private final List<IndicatorNamedSeries> series;
    public IndicatorResult(IndicatorType type, Map<String, Integer> parameters, int unstableBars, List<IndicatorNamedSeries> series) {
        this.indicatorType = type; this.parameters = Map.copyOf(parameters); this.unstableBars = unstableBars; this.series = List.copyOf(series);
    }
    public IndicatorType getIndicatorType() { return indicatorType; }
    public Map<String, Integer> getParameters() { return parameters; }
    public int getUnstableBars() { return unstableBars; }
    public List<IndicatorNamedSeries> getSeries() { return series; }
}
