package com.aiprovider.quant.strategy;

import java.util.List;
import java.util.Map;

public interface QuantStrategyDefinition {
    String code();
    String name();
    String version();
    String description();
    List<StrategyParameterDefinition> parameters();
    int minimumRequiredBars(Map<String, Integer> values);
    StrategyBuildResult build(Map<String, Integer> values, int barCount);
}
