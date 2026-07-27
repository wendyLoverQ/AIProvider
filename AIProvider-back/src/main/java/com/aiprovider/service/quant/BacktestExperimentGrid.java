package com.aiprovider.service.quant;

import com.aiprovider.quant.research.ParameterSpaceExpansion;
import com.aiprovider.quant.research.StrategyParameterSpaceExpander;
import com.aiprovider.quant.research.StrategyResearchException;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import java.util.List;
import java.util.Map;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;

/** Backward-compatible adapter over the Quant research-space expander. */
final class BacktestExperimentGrid {
  private BacktestExperimentGrid() {}

  static int candidateCount(Map<String, List<Integer>> input, QuantStrategyDefinition definition, int maxCandidates) {
    try {
      return new StrategyParameterSpaceExpander().expandExplicitGrid(definition, normalize(input, definition), maxCandidates).candidateCount();
    } catch (StrategyResearchException exception) {
      throw map(exception);
    }
  }

  static Result expand(Map<String, List<Integer>> input, QuantStrategyDefinition definition, int maxCandidates) {
    try {
      ParameterSpaceExpansion expansion = new StrategyParameterSpaceExpander().expandExplicitGrid(definition, normalize(input, definition), maxCandidates);
      return new Result(expansion.grid(), expansion.combinations());
    } catch (StrategyResearchException exception) {
      throw map(exception);
    }
  }

  private static Map<String, List<Integer>> normalize(Map<String, List<Integer>> input, QuantStrategyDefinition definition) {
    if (input == null) return null;
    java.util.LinkedHashMap<String, List<Integer>> ordered = new java.util.LinkedHashMap<>();
    for (StrategyParameterDefinition parameter : definition.parameters()) ordered.put(parameter.name(), input.get(parameter.name()));
    for (String key : input.keySet()) if (!ordered.containsKey(key)) ordered.put(key, input.get(key));
    return ordered;
  }

  private static BacktestTaskException map(StrategyResearchException exception) {
    String code = "STRATEGY_RESEARCH_SPACE_TOO_LARGE".equals(exception.getErrorCode())
        ? "WALK_FORWARD_TOO_LARGE" : "BACKTEST_EXPERIMENT_GRID_INVALID";
    return new BacktestTaskException(code, exception.getMessage());
  }

  record Result(Map<String, List<Integer>> grid, List<Map<String, Integer>> combinations) {}
}
