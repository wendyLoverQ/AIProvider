package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.mapper.BacktestEquityMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.util.*;
import org.springframework.stereotype.Service;

/** Centralizes all batch reads needed by Walk-forward status, result, and OOS views. */
@Service
public class WalkForwardStudySnapshotLoader {
  private final WalkForwardFoldMapper folds;
  private final BacktestExperimentService experiments;
  private final BacktestRunMapper runs;
  private final BacktestEquityMapper equity;

  public WalkForwardStudySnapshotLoader(
      WalkForwardFoldMapper folds,
      BacktestExperimentService experiments,
      BacktestRunMapper runs,
      BacktestEquityMapper equity) {
    this.folds = folds;
    this.experiments = experiments;
    this.runs = runs;
    this.equity = equity;
  }

  public WalkForwardStudySnapshot load(
      WalkForwardStudyRow study, List<WalkForwardFoldRow> foldRows, boolean includeEquity) {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardStudySnapshotLoader.load", new String[] { "study", "foldRows", "includeEquity" }, new Object[] { study, foldRows, includeEquity });
      return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudySnapshotLoader.load", loadMany(List.of(study), foldRows, includeEquity).get(study.studyId));
  }

  public Map<String, WalkForwardStudySnapshot> loadMany(
      List<WalkForwardStudyRow> studies, List<WalkForwardFoldRow> foldRows, boolean includeEquity) {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardStudySnapshotLoader.loadMany", new String[] { "studies", "foldRows", "includeEquity" }, new Object[] { studies, foldRows, includeEquity });
      if (studies == null || studies.isEmpty()) return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudySnapshotLoader.loadMany", Map.of());
    Map<String, List<WalkForwardFoldRow>> groupedFolds = new LinkedHashMap<>();
    for (WalkForwardStudyRow study : studies) groupedFolds.put(study.studyId, new ArrayList<>());
    for (WalkForwardFoldRow fold : foldRows)
      groupedFolds.computeIfAbsent(fold.studyId, ignored -> new ArrayList<>()).add(fold);

    Set<String> experimentIds = new LinkedHashSet<>();
    Set<String> runIds = new LinkedHashSet<>();
    for (WalkForwardFoldRow fold : foldRows) {
      if (fold.experimentId != null) experimentIds.add(fold.experimentId);
      if (fold.selectedTrainingRunId != null) runIds.add(fold.selectedTrainingRunId);
      if (fold.selectedValidationRunId != null) runIds.add(fold.selectedValidationRunId);
    }
    Map<String, BacktestExperimentDtos.ExperimentSummary> experimentRows =
        experiments.getMany(experimentIds);
    Map<String, BacktestRunRow> runRows = new LinkedHashMap<>();
    if (!runIds.isEmpty()) {
      for (BacktestRunRow row : runs.findByRunIds(runIds)) runRows.put(row.runId, row);
    }
    Map<String, List<BacktestEquityRow>> equityRows = new LinkedHashMap<>();
    if (includeEquity && !runIds.isEmpty()) {
      for (BacktestEquityRow row : equity.findAllByRunIds(runIds))
        equityRows.computeIfAbsent(row.runId, ignored -> new ArrayList<>()).add(row);
      equityRows.replaceAll((ignored, values) -> List.copyOf(values));
    }

    Map<String, WalkForwardStudySnapshot> result = new LinkedHashMap<>();
    for (WalkForwardStudyRow study : studies) {
      List<WalkForwardFoldRow> studyFolds =
          List.copyOf(groupedFolds.getOrDefault(study.studyId, List.of()));
      result.put(
          study.studyId,
          new WalkForwardStudySnapshot(
              study,
              studyFolds,
              experimentRows,
              Map.copyOf(runRows),
              Map.copyOf(equityRows)));
    }
    return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudySnapshotLoader.loadMany", Map.copyOf(result));
  }

  public Map<String, WalkForwardStudySnapshot> loadMany(
      List<WalkForwardStudyRow> studies, boolean includeEquity) {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardStudySnapshotLoader.loadMany", new String[] { "studies", "includeEquity" }, new Object[] { studies, includeEquity });
      if (studies == null || studies.isEmpty()) return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudySnapshotLoader.loadMany", Map.of());
    List<String> ids = studies.stream().map(row -> row.studyId).toList();
    List<WalkForwardFoldRow> foldRows = folds.findAllByStudyIds(ids);
    return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudySnapshotLoader.loadMany", loadMany(studies, foldRows, includeEquity));
  }
}
