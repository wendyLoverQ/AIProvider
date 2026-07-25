package com.aiprovider.quant.strategy;

import java.util.Map;

public final class StrategyBuildResult {
    private final String code;
    private final String version;
    private final int minimumRequiredBars;
    private final Map<String, Integer> parameters;
    public StrategyBuildResult(String code, String version, int minimumRequiredBars, Map<String, Integer> parameters) {
        this.code = code; this.version = version; this.minimumRequiredBars = minimumRequiredBars;
        this.parameters = Map.copyOf(parameters);
    }
    public String getCode() { return code; }
    public String getVersion() { return version; }
    public int getMinimumRequiredBars() { return minimumRequiredBars; }
    public Map<String, Integer> getParameters() { return parameters; }
}
