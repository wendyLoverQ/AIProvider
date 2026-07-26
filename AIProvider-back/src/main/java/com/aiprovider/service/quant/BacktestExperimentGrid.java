package com.aiprovider.service.quant;

import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;
import java.util.*;

/** Deterministic, side-effect-free expansion of an experiment parameter grid. */
final class BacktestExperimentGrid {
    private BacktestExperimentGrid() {}

    static Result expand(Map<String,List<Integer>> input, QuantStrategyDefinition definition, int maxCandidates) {
        if (input == null || input.size() != definition.parameters().size()) invalid("parameter grid keys do not match strategy definition");
        LinkedHashMap<String,List<Integer>> grid = new LinkedHashMap<>();
        for (StrategyParameterDefinition parameter : definition.parameters()) {
            List<Integer> values = input.get(parameter.name());
            if (values == null || values.isEmpty() || values.size() > 20
                    || new HashSet<>(values).size() != values.size()
                    || values.stream().anyMatch(value -> value == null || value < parameter.minValue() || value > parameter.maxValue())) {
                invalid("invalid values for " + parameter.name());
            }
            grid.put(parameter.name(), List.copyOf(values));
        }
        if (!grid.keySet().equals(input.keySet())) invalid("parameter grid keys do not match strategy definition");
        long total = 1;
        try {
            for (List<Integer> values : grid.values()) total = Math.multiplyExact(total, values.size());
        } catch (ArithmeticException e) {
            invalid("candidate count overflow");
        }
        long totalLegs;
        try { totalLegs=Math.multiplyExact(total,2); } catch (ArithmeticException e) { invalid("leg count overflow"); return null; }
        if (total > maxCandidates || total > 64 || totalLegs > 128) invalid("candidate count exceeds limit");
        List<Map<String,Integer>> combinations = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            int cursor = index;
            Map<String,Integer> selected = new HashMap<>();
            List<StrategyParameterDefinition> parameters = definition.parameters();
            for (int parameterIndex = parameters.size() - 1; parameterIndex >= 0; parameterIndex--) {
                StrategyParameterDefinition parameter = parameters.get(parameterIndex);
                List<Integer> values = grid.get(parameter.name());
                selected.put(parameter.name(), values.get(cursor % values.size()));
                cursor /= values.size();
            }
            LinkedHashMap<String,Integer> candidate = new LinkedHashMap<>();
            for (StrategyParameterDefinition parameter : parameters) candidate.put(parameter.name(), selected.get(parameter.name()));
            try { definition.minimumRequiredBars(candidate); }
            catch (StrategyException e) { invalid(e.getMessage()); }
            combinations.add(Collections.unmodifiableMap(candidate));
        }
        return new Result(Collections.unmodifiableMap(grid), List.copyOf(combinations));
    }

    private static void invalid(String message) { throw new BacktestTaskException("BACKTEST_EXPERIMENT_GRID_INVALID", message); }
    record Result(Map<String,List<Integer>> grid, List<Map<String,Integer>> combinations) {}
}
