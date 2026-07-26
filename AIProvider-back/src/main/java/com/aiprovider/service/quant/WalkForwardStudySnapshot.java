package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.util.List;
import java.util.Map;

/** Immutable, batch-loaded view used by every Walk-forward read path. */
public record WalkForwardStudySnapshot(
    WalkForwardStudyRow study,
    List<WalkForwardFoldRow> folds,
    Map<String, BacktestExperimentDtos.ExperimentSummary> experiments,
    Map<String, BacktestRunRow> runs,
    Map<String, List<BacktestEquityRow>> equities) {

  public BacktestExperimentDtos.ExperimentSummary experiment(String experimentId) {
    return experiments.get(experimentId);
  }

  public BacktestRunRow run(String runId) {
    return runId == null ? null : runs.get(runId);
  }

  public List<BacktestEquityRow> equity(String runId) {
    return runId == null ? List.of() : equities.getOrDefault(runId, List.of());
  }
}
