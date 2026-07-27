package com.aiprovider.quant.research;

import java.util.List;
import java.util.Map;

public record ParameterSpaceExpansion(StrategyResearchSpace space, Map<String, List<Integer>> grid,
                                      List<Map<String, Integer>> combinations, int candidateCount,
                                      int maximumRequiredBars) {
    public ParameterSpaceExpansion {
        if (space == null || grid == null || combinations == null) throw new IllegalArgumentException("expansion values must not be null");
        if (candidateCount != combinations.size()) throw new IllegalArgumentException("candidateCount does not match combinations");
        grid = copyGrid(grid);
        combinations = combinations.stream().map(ParameterSpaceExpansion::copyCombination).toList();
        combinations = List.copyOf(combinations);
    }

    private static Map<String, List<Integer>> copyGrid(Map<String, List<Integer>> source) {
        java.util.LinkedHashMap<String, List<Integer>> copy = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> copyCombination(Map<String, Integer> source) {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(source));
    }
}
