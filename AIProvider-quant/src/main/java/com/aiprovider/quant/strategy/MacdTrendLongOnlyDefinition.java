package com.aiprovider.quant.strategy;

import java.util.Map;

public final class MacdTrendLongOnlyDefinition implements QuantStrategyDefinition {
    public static final String CODE = "MACD_TREND_LONG_ONLY";
    private static final int DEFAULT_FAST = 12;
    private static final int DEFAULT_SLOW = 26;
    private static final int DEFAULT_SIGNAL = 9;

    @Override public String code() { return CODE; }
    @Override public String name() { return "MACD 趋势多头"; }
    @Override public String version() { return "1.0.0"; }
    @Override public String description() { return "MACD 上穿信号线做多，下穿信号线离场；仅用于历史研究，不代表盈利能力。"; }
    @Override public java.util.List<StrategyParameterDefinition> parameters() {
        return java.util.List.of(new StrategyParameterDefinition("fastPeriod", DEFAULT_FAST, 2, 500),
                new StrategyParameterDefinition("slowPeriod", DEFAULT_SLOW, 3, 1000),
                new StrategyParameterDefinition("signalPeriod", DEFAULT_SIGNAL, 2, 500));
    }
    @Override public int minimumRequiredBars(Map<String, Integer> values) { return calculateMinimumRequiredBars(resolve(values)); }
    @Override public StrategyBuildResult build(Map<String, Integer> values, int barCount) {
        Map<String, Integer> resolved = resolve(values);
        int minimum = calculateMinimumRequiredBars(resolved);
        if (barCount < minimum) throw new StrategyException("BACKTEST_INSUFFICIENT_BARS", "strategyCode=" + CODE + " barCount=" + barCount + " minimum=" + minimum);
        return new StrategyBuildResult(CODE, version(), minimum, resolved);
    }
    private int calculateMinimumRequiredBars(Map<String, Integer> resolved) {
        int fast = resolved.get("fastPeriod");
        int slow = resolved.get("slowPeriod");
        int signal = resolved.get("signalPeriod");
        if (fast < 2 || fast > 500 || slow < 3 || slow > 1000 || signal < 2 || signal > 500 || fast >= slow) {
            throw new StrategyException("BACKTEST_PARAMETER_INVALID", "strategyCode=" + CODE + " fastPeriod=" + fast + " slowPeriod=" + slow + " signalPeriod=" + signal);
        }
        return slow + signal + 1;
    }
    private Map<String, Integer> resolve(Map<String, Integer> values) {
        Map<String, Integer> input = values == null ? Map.of() : values;
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || (!entry.getKey().equals("fastPeriod") && !entry.getKey().equals("slowPeriod") && !entry.getKey().equals("signalPeriod"))) {
                throw new StrategyException("BACKTEST_PARAMETER_INVALID", "strategyCode=" + CODE + " parameter=" + entry.getKey() + " value=" + entry.getValue());
            }
        }
        return Map.of("fastPeriod", input.getOrDefault("fastPeriod", DEFAULT_FAST), "slowPeriod", input.getOrDefault("slowPeriod", DEFAULT_SLOW), "signalPeriod", input.getOrDefault("signalPeriod", DEFAULT_SIGNAL));
    }
}
