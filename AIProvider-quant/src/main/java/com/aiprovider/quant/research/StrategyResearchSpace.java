package com.aiprovider.quant.research;

import java.util.HashSet;
import java.util.List;

public record StrategyResearchSpace(String strategyCode, String strategyVersion, List<IntegerParameterRange> parameters) {
    public StrategyResearchSpace {
        if (strategyCode == null || strategyCode.isBlank()) throw new IllegalArgumentException("strategyCode must not be blank");
        if (strategyVersion == null || strategyVersion.isBlank()) throw new IllegalArgumentException("strategyVersion must not be blank");
        if (parameters == null || parameters.isEmpty()) throw new IllegalArgumentException("parameters must not be empty");
        if (parameters.stream().anyMatch(parameter -> parameter == null)) throw new IllegalArgumentException("parameters must not contain null");
        if (new HashSet<>(parameters.stream().map(IntegerParameterRange::parameterName).toList()).size() != parameters.size()) {
            throw new IllegalArgumentException("parameter names must be unique");
        }
        parameters = List.copyOf(parameters);
    }
}
