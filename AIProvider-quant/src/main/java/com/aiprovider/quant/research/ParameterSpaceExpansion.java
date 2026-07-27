package com.aiprovider.quant.research;

import java.util.List;
import java.util.Map;

public record ParameterSpaceExpansion(ParameterSpaceKind kind, StrategyResearchSpace rangeSpace,
                                      Map<String, List<Integer>> grid,
                                      List<Map<String, Integer>> combinations, int candidateCount,
                                      int maximumRequiredBars) {
    public ParameterSpaceExpansion {
        if (kind == null || grid == null || combinations == null) throw new IllegalArgumentException("expansion values must not be null");
        if (grid.isEmpty() || combinations.isEmpty()) throw new IllegalArgumentException("expansion values must not be empty");
        if (candidateCount <= 0) throw new IllegalArgumentException("candidateCount must be positive");
        if (candidateCount != combinations.size()) throw new IllegalArgumentException("candidateCount does not match combinations");
        if (maximumRequiredBars <= 0) throw new IllegalArgumentException("maximumRequiredBars must be positive");
        if (kind == ParameterSpaceKind.INTEGER_RANGE && rangeSpace == null) throw new IllegalArgumentException("rangeSpace is required for integer ranges");
        if (kind == ParameterSpaceKind.EXPLICIT_GRID && rangeSpace != null) throw new IllegalArgumentException("rangeSpace must be null for explicit grids");
        grid = copyGrid(grid);
        java.util.List<String> parameterOrder = List.copyOf(grid.keySet());
        combinations = combinations.stream().map(combination -> copyCombination(combination, parameterOrder)).toList();
        combinations = List.copyOf(combinations);
    }

    private static Map<String, List<Integer>> copyGrid(Map<String, List<Integer>> source) {
        java.util.LinkedHashMap<String, List<Integer>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isEmpty() || value.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("grid must contain non-empty values");
            }
            for (int i = 1; i < value.size(); i++) {
                if (value.get(i) <= value.get(i - 1)) throw new IllegalArgumentException("grid values must be strictly increasing");
            }
            copy.put(key, List.copyOf(value));
        });
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> copyCombination(Map<String, Integer> source, List<String> parameterOrder) {
        if (source == null || source.size() != parameterOrder.size() || !List.copyOf(source.keySet()).equals(parameterOrder)
                || source.values().stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("combination keys must match grid order");
        }
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(source));
    }
}
