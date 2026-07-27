package com.aiprovider.quant.strategy;

import com.aiprovider.quant.research.IntegerParameterRange;
import com.aiprovider.quant.research.StrategyResearchSpace;
import java.util.List;
import java.util.Map;

public final class EmaCrossLongOnlyDefinition implements QuantStrategyDefinition {
    public static final String CODE = "EMA_CROSS_LONG_ONLY";
    public String code() { return CODE; }
    public String name() { return "EMA 双均线多头"; }
    public String version() { return "1.0.0"; }
    public String description() { return "仅用于验证指标、规则和回测链路，不代表盈利能力。"; }
    public List<StrategyParameterDefinition> parameters() { return List.of(new StrategyParameterDefinition("fastPeriod", 12, 2, 1000), new StrategyParameterDefinition("slowPeriod", 26, 2, 1000)); }
    public StrategyResearchSpace researchSpace() { return new StrategyResearchSpace(code(), version(), List.of(new IntegerParameterRange("fastPeriod", 5, 20, 5), new IntegerParameterRange("slowPeriod", 30, 70, 20))); }
    public int minimumRequiredBars(Map<String, Integer> values) { return calculateMinimumRequiredBars(resolve(values)); }
    public StrategyBuildResult build(Map<String, Integer> values, int barCount) {
        Map<String, Integer> resolved = resolve(values);
        int fast = resolved.get("fastPeriod"), slow = resolved.get("slowPeriod");
        int minimumBars = calculateMinimumRequiredBars(resolved);
        if (barCount < minimumBars) throw new StrategyException("BACKTEST_INSUFFICIENT_BARS", "barCount=" + barCount + " minimum=" + minimumBars);
        return new StrategyBuildResult(code(), version(), minimumBars, resolved);
    }

    private int calculateMinimumRequiredBars(Map<String, Integer> resolved) {
        int fast = resolved.get("fastPeriod"), slow = resolved.get("slowPeriod");
        if (fast < 2 || slow < 2 || fast >= slow || slow > 1000) {
            throw new StrategyException("BACKTEST_PARAMETER_INVALID", "fastPeriod=" + fast + " slowPeriod=" + slow);
        }
        return slow + 1;
    }

    private Map<String, Integer> resolve(Map<String, Integer> values) {
        Map<String, Integer> input = values == null ? Map.of() : values;
        if (input.keySet().stream().anyMatch(key -> !key.equals("fastPeriod") && !key.equals("slowPeriod"))) {
            throw new StrategyException("BACKTEST_PARAMETER_INVALID", "unknown strategy parameter");
        }
        return Map.of("fastPeriod", input.getOrDefault("fastPeriod", 12), "slowPeriod", input.getOrDefault("slowPeriod", 26));
    }
}
