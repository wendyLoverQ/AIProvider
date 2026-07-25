package com.aiprovider.quant.indicator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IndicatorRequest {
    private final IndicatorType indicatorType;
    private final Map<String, Integer> parameters;

    public IndicatorRequest(IndicatorType indicatorType, Map<String, Integer> parameters) {
        this.indicatorType = indicatorType;
        this.parameters = parameters == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
    public IndicatorType getIndicatorType() { return indicatorType; }
    public Map<String, Integer> getParameters() { return parameters; }
}
