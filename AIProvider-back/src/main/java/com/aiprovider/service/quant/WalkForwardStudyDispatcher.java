package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantWalkForwardProperties;
import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.aiprovider.mapper.row.WalkForwardTrainingCandidateRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardStudyDispatcher {
  private static final Logger log = LogManager.getLogger(WalkForwardStudyDispatcher.class);
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final BacktestExperimentCreationService creation;
  private final BacktestRunMapper runs;
  private final ObjectMapper json;
  private final QuantWalkForwardProperties properties;
  private final WalkForwardStudySnapshotLoader snapshots;
  private final WalkForwardStudyService service;

  public WalkForwardStudyDispatcher(
      WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds,
      BacktestExperimentCreationService creation,
      BacktestRunMapper runs,
      ObjectMapper json,
      QuantWalkForwardProperties properties,
      WalkForwardStudySnapshotLoader snapshots,
      WalkForwardStudyService service) {
    this.studies = studies;
    this.folds = folds;
    this.creation = creation;
    this.runs = runs;
    this.json = json;
    this.properties = properties;
    this.snapshots = snapshots;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${quant.walk-forward.dispatcher-fixed-delay-ms:3000}")
  public void tick() {
  com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardStudyDispatcher.tick", new String[] {}, new Object[] {});
  Instant now = Instant.now();
    try {
      int reset = folds.resetStaleCreatingClaims(now.minusSeconds(properties.getStaleClaimSeconds()), now);
      log.info("operation=walk-forward-reset-stale affectedRows={} result=success", reset);
    } catch (RuntimeException failure) {
      log.error("operation=walk-forward-reset-stale result=retryable", failure);
      {
          com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
          return;
      }
    }
    List<WalkForwardStudyRow> studyRows = studies.findNonTerminal();
    if (studyRows.isEmpty()) {
        com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
        return;
    }
    List<String> studyIds = studyRows.stream().map(row -> row.studyId).toList();
    List<WalkForwardFoldRow> foldRows;
    try {
      foldRows = folds.findAllByStudyIds(studyIds);
    } catch (RuntimeException failure) {
      log.error("operation=walk-forward-fold-batch-load result=retryable", failure);
      {
          com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
          return;
      }
    }
    Map<String, WalkForwardStudySnapshot> loaded;
    try {
      loaded = snapshots.loadMany(studyRows, foldRows, false);
    } catch (RuntimeException failure) {
      WalkForwardDispatchErrorClassifier.Classification classified =
          WalkForwardDispatchErrorClassifier.classify(failure);
      log.error("operation=walk-forward-batch-load result=" + classified.kind() + " errorCode=" + classified.errorCode(), failure);
      if (classified.kind() == WalkForwardDispatchErrorClassifier.Kind.RETRYABLE) {
          com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
          return;
      }
      loaded = loadStudiesIndependently(studyRows, classified);
    }

    List<WaitingWork> waiting = new ArrayList<>();
    for (WalkForwardStudyRow study : studyRows) {
      WalkForwardStudySnapshot snapshot = loaded.get(study.studyId);
      if (snapshot == null) continue;
      try {
        WalkForwardStudyDtos.StudySummary before = service.refreshAggregate(snapshot);
        if (terminal(before.status())) continue;
        processStudy(study, snapshot, waiting);
      } catch (RuntimeException failure) {
        handleStudyFailure(study, snapshot, failure);
      }
    }

    if (!waiting.isEmpty()) {
      List<String> runIds = waiting.stream().map(work -> work.selected.validationRunId).toList();
      Map<String, BacktestRunRow> validationRuns = new LinkedHashMap<>();
      try {
        for (BacktestRunRow run : runs.findByRunIds(runIds)) validationRuns.put(run.runId, run);
      } catch (RuntimeException failure) {
        log.error("operation=walk-forward-validation-batch-load result=retryable", failure);
        {
            com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
            return;
        }
      }
      for (WaitingWork work : waiting) {
        try {
          processWaiting(work, validationRuns.get(work.selected.validationRunId));
        } catch (RuntimeException failure) {
          handleStudyFailure(work.study, work.snapshot, failure);
        }
      }
    }
    refreshAggregatesAfterDispatch(studyRows);
    com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyDispatcher.tick", null);
  }

  void recoverStaleClaims(long staleClaimSeconds) {
    Instant now = Instant.now();
    int affected = folds.resetStaleCreatingClaims(now.minusSeconds(staleClaimSeconds), now);
    log.info("operation=walk-forward-recovery-reset affectedRows={} result=success", affected);
  }

  private void processCreating(WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    String token = fold.claimToken;
    if (token == null) return;
    try {
      creation.createWithExperimentId(fold.experimentId, request(study, fold));
      int affected = folds.markWaitingExperiment(fold.foldId, token, Instant.now());
      expectSingle("markWaitingExperiment", study, fold, affected);
    } catch (RuntimeException failure) {
      handleFailure(study, fold, failure);
    }
  }

  private void processStudy(
      WalkForwardStudyRow study, WalkForwardStudySnapshot snapshot, List<WaitingWork> waiting) {
    WalkForwardFoldRow creatingFold =
        snapshot.folds().stream()
            .filter(f -> "CREATING_EXPERIMENT".equals(f.status))
            .findFirst()
            .orElse(null);
    if (creatingFold != null) {
      processCreating(study, creatingFold);
      return;
    }
    WalkForwardFoldRow waitingFold =
        snapshot.folds().stream()
            .filter(f -> "WAITING_EXPERIMENT".equals(f.status))
            .findFirst()
            .orElse(null);
    if (waitingFold != null) {
      WaitingWork work = prepareWaiting(study, waitingFold, snapshot);
      if (work != null) waiting.add(work);
      return;
    }
    if (snapshot.folds().stream().anyMatch(f -> "PENDING".equals(f.status))) claimNext(study);
  }

  private Map<String, WalkForwardStudySnapshot> loadStudiesIndependently(
      List<WalkForwardStudyRow> studyRows,
      WalkForwardDispatchErrorClassifier.Classification batchFailure) {
    Map<String, WalkForwardStudySnapshot> result = new LinkedHashMap<>();
    for (WalkForwardStudyRow study : studyRows) {
      List<WalkForwardFoldRow> rows = List.of();
      try {
        rows = folds.findAllByStudyId(study.studyId);
        result.put(study.studyId, snapshots.load(study, rows, false));
      } catch (RuntimeException failure) {
        WalkForwardStudySnapshot fallback =
            new WalkForwardStudySnapshot(study, rows, Map.of(), Map.of(), Map.of());
        handleStudyFailure(study, fallback, failure);
      }
    }
    return result;
  }

  private void refreshAggregatesAfterDispatch(List<WalkForwardStudyRow> studyRows) {
    List<String> studyIds = studyRows.stream().map(row -> row.studyId).toList();
    List<WalkForwardFoldRow> rows;
    try {
      rows = folds.findAllByStudyIds(studyIds);
    } catch (RuntimeException failure) {
      log.error("operation=walk-forward-post-dispatch-fold-load result=retryable", failure);
      return;
    }
    Map<String, WalkForwardStudySnapshot> loaded;
    try {
      loaded = snapshots.loadMany(studyRows, rows, false);
    } catch (RuntimeException failure) {
      WalkForwardDispatchErrorClassifier.Classification classified =
          WalkForwardDispatchErrorClassifier.classify(failure);
      if (classified.kind() == WalkForwardDispatchErrorClassifier.Kind.RETRYABLE) {
        log.error("operation=walk-forward-post-dispatch-batch-load result=retryable", failure);
        return;
      }
      loaded = loadStudiesIndependently(studyRows, classified);
    }
    for (WalkForwardStudyRow study : studyRows) {
      WalkForwardStudySnapshot snapshot = loaded.get(study.studyId);
      if (snapshot == null) continue;
      try {
        service.refreshAggregate(snapshot);
      } catch (RuntimeException failure) {
        handleStudyFailure(study, snapshot, failure);
      }
    }
  }

  private void handleStudyFailure(
      WalkForwardStudyRow study, WalkForwardStudySnapshot snapshot, RuntimeException failure) {
    WalkForwardDispatchErrorClassifier.Classification classified =
        WalkForwardDispatchErrorClassifier.classify(failure);
    if (classified.kind() == WalkForwardDispatchErrorClassifier.Kind.RETRYABLE) {
      log.warn(
          "operation=walk-forward-study studyId={} errorCode={} result=retryable",
          study.studyId,
          classified.errorCode(),
          failure);
      return;
    }
    WalkForwardFoldRow active =
        snapshot.folds().stream()
            .filter(
                fold ->
                    "CREATING_EXPERIMENT".equals(fold.status)
                        || "WAITING_EXPERIMENT".equals(fold.status))
            .findFirst()
            .orElse(null);
    if (active == null) {
      log.error(
          "operation=walk-forward-study studyId={} errorCode={} result=permanent_without_active_fold",
          study.studyId,
          classified.errorCode(),
          failure);
      return;
    }
    markFailed(study, active, classified);
  }

  private WaitingWork prepareWaiting(
      WalkForwardStudyRow study, WalkForwardFoldRow fold, WalkForwardStudySnapshot snapshot) {
    BacktestExperimentDtos.ExperimentSummary experiment = snapshot.experiment(fold.experimentId);
    if (experiment == null)
      throw new WalkForwardTaskException("WALK_FORWARD_EXPERIMENT_NOT_FOUND", "experimentId=" + fold.experimentId);
    if (!Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(experiment.status())) return null;
    WalkForwardSelectionMetric metric;
    try { metric = WalkForwardSelectionMetric.valueOf(study.selectionMetric); }
    catch (IllegalArgumentException e) { throw new WalkForwardTaskException("WALK_FORWARD_EXPERIMENT_INVALID", "selectionMetric is invalid"); }
    WalkForwardTrainingCandidateRow selected = findBest(fold.experimentId, metric, study.minimumTrainTrades);
    if (selected == null)
      throw new WalkForwardTaskException("WALK_FORWARD_NO_ELIGIBLE_CANDIDATE", "no eligible completed TRAIN candidate");
    return new WaitingWork(study, fold, selected, snapshot);
  }

  private void processWaiting(WaitingWork work, BacktestRunRow validation) {
    WalkForwardFoldRow fold = work.fold;
    try {
      if (validation == null || !"COMPLETED".equals(validation.status))
        throw new WalkForwardTaskException("WALK_FORWARD_SELECTED_VALIDATION_FAILED", "selected validation run is not completed");
      int affected = folds.completeSelection(fold.foldId, work.selected.candidateId, work.selected.parametersJson, work.selected.trainingRunId, work.selected.validationRunId, work.selected.metricValue, Instant.now());
      expectSingle("completeSelection", work.study, fold, affected);
    } catch (RuntimeException failure) {
      handleFailure(work.study, fold, failure);
    }
  }

  private void claimNext(WalkForwardStudyRow study) {
    String token = UUID.randomUUID().toString();
    int affected = folds.claimNextPending(study.studyId, token, Instant.now());
    if (affected > 1) throw stateConflict("claimNextPending affected more than one row");
    log.info("operation=claimNextPending studyId={} affectedRows={} result={}", study.studyId, affected, affected == 1 ? "claimed" : "cas_lost");
  }

  private void handleFailure(WalkForwardStudyRow study, WalkForwardFoldRow fold, RuntimeException failure) {
    WalkForwardDispatchErrorClassifier.Classification classified = WalkForwardDispatchErrorClassifier.classify(failure);
    if (classified.kind() == WalkForwardDispatchErrorClassifier.Kind.PERMANENT) {
      markFailed(study, fold, classified);
    } else {
      if ("CREATING_EXPERIMENT".equals(fold.status) && fold.claimToken != null) {
        int affected = folds.releaseCreationClaim(fold.foldId, fold.claimToken, Instant.now());
        if (affected > 1) throw stateConflict("releaseCreationClaim affected more than one row");
        log.warn("operation=releaseCreationClaim studyId={} foldId={} experimentId={} claimToken={} errorCode={} affectedRows={} result=retryable", study.studyId, fold.foldId, fold.experimentId, fold.claimToken, classified.errorCode(), affected);
      } else {
        log.warn("operation=walk-forward-dispatch studyId={} foldId={} experimentId={} errorCode={} affectedRows=0 result=retryable", study.studyId, fold.foldId, fold.experimentId, classified.errorCode(), failure);
      }
    }
  }

  private void markFailed(WalkForwardStudyRow study, WalkForwardFoldRow fold, WalkForwardDispatchErrorClassifier.Classification classified) {
    int affected = folds.markFailed(fold.foldId, classified.errorCode(), classified.errorMessage(), Instant.now());
    if (affected > 1) throw stateConflict("markFailed affected more than one row");
    if (affected == 0) {
      WalkForwardFoldRow current = folds.findByFoldId(fold.foldId);
      log.warn("operation=markFailed studyId={} foldId={} experimentId={} errorCode={} affectedRows=0 result=cas_lost currentStatus={}", study.studyId, fold.foldId, fold.experimentId, classified.errorCode(), current == null ? "MISSING" : current.status);
      return;
    }
    log.error("operation=markFailed studyId={} foldId={} experimentId={} errorCode={} affectedRows=1 result=failed", study.studyId, fold.foldId, fold.experimentId, classified.errorCode());
  }

  private void expectSingle(String operation, WalkForwardStudyRow study, WalkForwardFoldRow fold, int affected) {
    if (affected > 1) throw stateConflict(operation + " affected more than one row");
    log.info("operation={} studyId={} foldId={} experimentId={} claimToken={} affectedRows={} result={}", operation, study.studyId, fold.foldId, fold.experimentId, fold.claimToken, affected, affected == 1 ? "success" : "cas_lost");
  }

  private WalkForwardTrainingCandidateRow findBest(String experimentId, WalkForwardSelectionMetric metric, int minimumTrainTrades) {
    return switch (metric) {
      case TRAIN_TOTAL_RETURN_RATIO -> folds.findBestByTrainTotalReturnRatio(experimentId, minimumTrainTrades);
      case TRAIN_PROFIT_FACTOR -> folds.findBestByTrainProfitFactor(experimentId, minimumTrainTrades);
      case TRAIN_NET_PROFIT -> folds.findBestByTrainNetProfit(experimentId, minimumTrainTrades);
      case TRAIN_WIN_RATE -> folds.findBestByTrainWinRate(experimentId, minimumTrainTrades);
      case TRAIN_MAXIMUM_DRAWDOWN_RATIO -> folds.findBestByTrainMaximumDrawdownRatio(experimentId, minimumTrainTrades);
    };
  }

  private BacktestExperimentCreateRequest request(WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    BacktestExperimentCreateRequest request = new BacktestExperimentCreateRequest();
    request.setDatasetId(study.datasetId); request.setStrategyCode(study.strategyCode); request.setStrategyVersion(study.strategyVersion);
    request.setExecutionProfileCode(study.executionProfileCode); request.setDirectionMode(study.directionMode); request.setOrderSizingMode(study.orderSizingMode);
    request.setParameterGrid(readGrid(study.parameterGridJson));
    request.setTrainingStartOpenTimeInclusive(Instant.ofEpochMilli(fold.trainingStartOpenTimeMs)); request.setTrainingEndOpenTimeExclusive(Instant.ofEpochMilli(fold.trainingEndOpenTimeMs));
    request.setValidationStartOpenTimeInclusive(Instant.ofEpochMilli(fold.validationStartOpenTimeMs)); request.setValidationEndOpenTimeExclusive(Instant.ofEpochMilli(fold.validationEndOpenTimeMs));
    request.setInitialCapital(study.initialCapital); request.setOrderAmount(study.orderAmount); request.setFeeRate(study.feeRate); request.setForceCloseAtEnd(study.forceCloseAtEnd);
    return request;
  }

  private Map<String, List<Integer>> readGrid(String value) {
    try { return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {}); }
    catch (Exception e) { throw new WalkForwardTaskException("WALK_FORWARD_EXPERIMENT_INVALID", "stored grid JSON is invalid"); }
  }

  private WalkForwardTaskException stateConflict(String message) { return new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", message); }
  private boolean terminal(String status) {
    return Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(status);
  }

  private record WaitingWork(
      WalkForwardStudyRow study,
      WalkForwardFoldRow fold,
      WalkForwardTrainingCandidateRow selected,
      WalkForwardStudySnapshot snapshot) {}
}
