package com.aiprovider.quant.strategy;

public final class StrategyBuildResult {
    private final String code;
    private final String version;
    private final int minimumRequiredBars;
    public StrategyBuildResult(String code, String version, int minimumRequiredBars) { this.code = code; this.version = version; this.minimumRequiredBars = minimumRequiredBars; }
    public String getCode() { return code; }
    public String getVersion() { return version; }
    public int getMinimumRequiredBars() { return minimumRequiredBars; }
}
