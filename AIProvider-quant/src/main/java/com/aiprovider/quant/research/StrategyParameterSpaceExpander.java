package com.aiprovider.quant.research;

import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrategyParameterSpaceExpander {
    public static final int MAXIMUM_CANDIDATES = 64;
    public static final int MAXIMUM_VALUES_PER_PARAMETER = 20;

    public ParameterSpaceExpansion expand(QuantStrategyDefinition definition, StrategyResearchSpace space, int maximumCandidates) {
        require(definition != null, "definition must not be null");
        require(space != null, "space must not be null");
        validateMaximum(maximumCandidates);
        require(space.strategyCode().equals(definition.code()), "strategy code does not match definition");
        require(space.strategyVersion().equals(definition.version()), "strategy version does not match definition");
        List<StrategyParameterDefinition> definitions = definition.parameters();
        require(space.parameters().size() == definitions.size(), "parameter count does not match definition");
        LinkedHashMap<String, List<Integer>> grid = new LinkedHashMap<>();
        for (int i = 0; i < definitions.size(); i++) {
            StrategyParameterDefinition parameter = definitions.get(i);
            IntegerParameterRange range = space.parameters().get(i);
            require(range.parameterName().equals(parameter.name()), "parameter order does not match definition");
            grid.put(range.parameterName(), range.values());
        }
        return expandNormalizedGrid(definition, ParameterSpaceKind.INTEGER_RANGE, space, grid, maximumCandidates);
    }

    public ParameterSpaceExpansion expandExplicitGrid(QuantStrategyDefinition definition, Map<String, List<Integer>> input, int maximumCandidates) {
        require(definition != null, "definition must not be null");
        validateMaximum(maximumCandidates);
        require(input != null, "parameter grid must not be null");
        List<StrategyParameterDefinition> definitions = definition.parameters();
        require(input.size() == definitions.size(), "parameter grid keys do not match definition");
        LinkedHashMap<String, List<Integer>> grid = new LinkedHashMap<>();
        for (StrategyParameterDefinition parameter : definitions) {
            require(input.containsKey(parameter.name()), "parameter grid keys do not match definition");
            List<Integer> values = input.get(parameter.name());
            require(values != null, "invalid values for " + parameter.name());
            grid.put(parameter.name(), values);
        }
        require(new ArrayList<>(input.keySet()).equals(new ArrayList<>(grid.keySet())), "parameter order does not match definition");
        return expandNormalizedGrid(definition, ParameterSpaceKind.EXPLICIT_GRID, null, grid, maximumCandidates);
    }

    private ParameterSpaceExpansion expandNormalizedGrid(QuantStrategyDefinition definition, ParameterSpaceKind kind,
                                                         StrategyResearchSpace rangeSpace, LinkedHashMap<String, List<Integer>> input,
                                                         int maximumCandidates) {
        LinkedHashMap<String, List<Integer>> grid = new LinkedHashMap<>();
        for (StrategyParameterDefinition parameter : definition.parameters()) {
            List<Integer> values = input.get(parameter.name());
            require(values != null && !values.isEmpty(), "invalid values for " + parameter.name());
            if (values.size() > MAXIMUM_VALUES_PER_PARAMETER) throw tooLarge("too many values for " + parameter.name());
            Integer previous = null;
            for (Integer value : values) {
                require(value != null && value >= parameter.minValue() && value <= parameter.maxValue(), "invalid values for " + parameter.name());
                require(previous == null || value > previous, "values must be strictly increasing for " + parameter.name());
                previous = value;
            }
            grid.put(parameter.name(), List.copyOf(values));
        }
        long count = 1;
        try { for (List<Integer> values : grid.values()) count = Math.multiplyExact(count, values.size()); }
        catch (ArithmeticException exception) { throw tooLarge("candidate count overflow"); }
        if (count > maximumCandidates || count > MAXIMUM_CANDIDATES) throw tooLarge("candidate count exceeds capacity");
        List<Map<String, Integer>> combinations = new ArrayList<>();
        int maximumRequiredBars = Integer.MIN_VALUE;
        for (int index = 0; index < (int) count; index++) {
            int cursor = index;
            LinkedHashMap<String, Integer> candidate = new LinkedHashMap<>();
            List<StrategyParameterDefinition> parameters = definition.parameters();
            for (int i = parameters.size() - 1; i >= 0; i--) {
                String name = parameters.get(i).name();
                List<Integer> values = grid.get(name);
                candidate.put(name, values.get(cursor % values.size()));
                cursor /= values.size();
            }
            LinkedHashMap<String, Integer> ordered = new LinkedHashMap<>();
            for (StrategyParameterDefinition parameter : parameters) ordered.put(parameter.name(), candidate.get(parameter.name()));
            try {
                maximumRequiredBars = Math.max(maximumRequiredBars, definition.minimumRequiredBars(ordered));
            } catch (StrategyException exception) {
                throw invalid(exception.getMessage());
            }
            combinations.add(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ordered)));
        }
        return new ParameterSpaceExpansion(kind, rangeSpace, grid, combinations, combinations.size(), maximumRequiredBars);
    }

    private static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    private static void validateMaximum(int maximumCandidates) { require(maximumCandidates > 0, "maximumCandidates must be positive"); }
    private static StrategyResearchException invalid(String message) { return new StrategyResearchException("STRATEGY_RESEARCH_SPACE_INVALID", message); }
    private static StrategyResearchException tooLarge(String message) { return new StrategyResearchException("STRATEGY_RESEARCH_SPACE_TOO_LARGE", message); }
}
