package com.aiprovider.quant.strategy;

import java.util.Map;

public final class RsiMeanReversionLongOnlyDefinition implements QuantStrategyDefinition {
    public static final String CODE = "RSI_MEAN_REVERSION_LONG_ONLY";
    private static final int DEFAULT_PERIOD = 14;
    private static final int DEFAULT_ENTRY = 30;
    private static final int DEFAULT_EXIT = 55;

    @Override public String code() { return CODE; }
    @Override public String name() { return "RSI 均值回归多头"; }
    @Override public String version() { return "1.0.0"; }
    @Override public String description() { return "RSI 进入超卖区后做多，恢复到退出阈值离场；仅用于历史研究，不代表盈利能力。"; }
    @Override public java.util.List<StrategyParameterDefinition> parameters() {
        return java.util.List.of(new StrategyParameterDefinition("rsiPeriod", DEFAULT_PERIOD, 2, 500),
                new StrategyParameterDefinition("entryThreshold", DEFAULT_ENTRY, 1, 49),
                new StrategyParameterDefinition("exitThreshold", DEFAULT_EXIT, 51, 99));
    }
    @Override public int minimumRequiredBars(Map<String, Integer> values) { return calculateMinimumRequiredBars(resolve(values)); }
    @Override public StrategyBuildResult build(Map<String, Integer> values, int barCount) {
        Map<String, Integer> resolved = resolve(values);
        int minimum = calculateMinimumRequiredBars(resolved);
        if (barCount < minimum) throw new StrategyException("BACKTEST_INSUFFICIENT_BARS", "strategyCode=" + CODE + " barCount=" + barCount + " minimum=" + minimum);
        return new StrategyBuildResult(CODE, version(), minimum, resolved);
    }
    private int calculateMinimumRequiredBars(Map<String, Integer> resolved) {
        int period = resolved.get("rsiPeriod");
        int entry = resolved.get("entryThreshold");
        int exit = resolved.get("exitThreshold");
        if (period < 2 || period > 500 || entry < 1 || entry > 49 || exit < 51 || exit > 99 || entry >= exit) {
            throw new StrategyException("BACKTEST_PARAMETER_INVALID", "strategyCode=" + CODE + " rsiPeriod=" + period + " entryThreshold=" + entry + " exitThreshold=" + exit);
        }
        return period + 1;
    }
    private Map<String, Integer> resolve(Map<String, Integer> values) {
        Map<String, Integer> input = values == null ? Map.of() : values;
        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || (!entry.getKey().equals("rsiPeriod") && !entry.getKey().equals("entryThreshold") && !entry.getKey().equals("exitThreshold"))) {
                throw new StrategyException("BACKTEST_PARAMETER_INVALID", "strategyCode=" + CODE + " parameter=" + entry.getKey() + " value=" + entry.getValue());
            }
        }
        return Map.of("rsiPeriod", input.getOrDefault("rsiPeriod", DEFAULT_PERIOD), "entryThreshold", input.getOrDefault("entryThreshold", DEFAULT_ENTRY), "exitThreshold", input.getOrDefault("exitThreshold", DEFAULT_EXIT));
    }
}
