package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantWalkForwardProperties;
import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
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
  private static final Set<String> PERMANENT =
      Set.of(
          "WALK_FORWARD_REQUEST_INVALID",
          "WALK_FORWARD_WINDOW_INVALID",
          "WALK_FORWARD_TOO_LARGE",
          "WALK_FORWARD_EXPERIMENT_CONFLICT",
          "WALK_FORWARD_STATE_CONFLICT",
          "BACKTEST_EXPERIMENT_GRID_INVALID",
          "BACKTEST_EXPERIMENT_RANGE_INVALID");
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final BacktestExperimentCreationService creation;
  private final WalkForwardStudyService service;
  private final BacktestExperimentService experiments;
  private final BacktestRunMapper runs;
  private final ObjectMapper json;
  private final QuantWalkForwardProperties properties;

  public WalkForwardStudyDispatcher(
      WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds,
      BacktestExperimentCreationService creation,
      WalkForwardStudyService service,
      BacktestExperimentService experiments,
      BacktestRunMapper runs,
      ObjectMapper json,
      QuantWalkForwardProperties properties) {
    this.studies = studies;
    this.folds = folds;
    this.creation = creation;
    this.service = service;
    this.experiments = experiments;
    this.runs = runs;
    this.json = json;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${quant.walk-forward.dispatcher-fixed-delay-ms:3000}")
  public void tick() {
    Instant now = Instant.now();
    try {
      folds.resetStaleCreatingClaims(now.minusSeconds(properties.getStaleClaimSeconds()), now);
    } catch (RuntimeException e) {
      log.warn("walk-forward stale claim reset failed", e);
    }
    for (WalkForwardStudyRow study : studies.findNonTerminal()) {
      try {
        processStudy(study);
      } catch (RuntimeException e) {
        log.error("walk-forward study tick failed studyId=" + study.studyId, e);
      }
    }
  }

  void recoverStaleClaims(long staleClaimSeconds) {
    Instant now = Instant.now();
    folds.resetStaleCreatingClaims(now.minusSeconds(staleClaimSeconds), now);
  }

  private void processStudy(WalkForwardStudyRow study) {
    service.get(study.studyId);
    List<WalkForwardFoldRow> all = folds.findAllByStudyId(study.studyId);
    WalkForwardFoldRow creating =
        all.stream().filter(f -> "CREATING_EXPERIMENT".equals(f.status)).findFirst().orElse(null);
    if (creating != null) {
      processCreating(study, creating);
      service.get(study.studyId);
      return;
    }
    WalkForwardFoldRow waiting =
        all.stream().filter(f -> "WAITING_EXPERIMENT".equals(f.status)).findFirst().orElse(null);
    if (waiting != null) {
      processWaiting(study, waiting);
      service.get(study.studyId);
      return;
    }
    if (all.stream().noneMatch(f -> "PENDING".equals(f.status))) {
      service.get(study.studyId);
      return;
    }
    int claimed =
        folds.claimNextPending(study.studyId, UUID.randomUUID().toString(), Instant.now());
    if (claimed > 1) throw error("WALK_FORWARD_STATE_CONFLICT", "claim affected multiple folds");
    service.get(study.studyId);
  }

  private void processCreating(WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    String token = fold.claimToken;
    if (token == null) return;
    try {
      BacktestExperimentCreateRequest request = request(study, fold);
      creation.createWithExperimentId(fold.experimentId, request);
      if (folds.markWaitingExperiment(fold.foldId, token, Instant.now()) != 1)
        throw error(
            "WALK_FORWARD_STATE_CONFLICT", "mark waiting affected an unexpected number of rows");
    } catch (RuntimeException exception) {
      String code = code(exception);
      if (isPermanent(exception, code)) markFailed(fold, code, message(exception));
      else if (folds.releaseCreationClaim(fold.foldId, token, Instant.now()) == 0)
        log.warn("walk-forward creation claim lost foldId=" + fold.foldId);
    }
  }

  private void processWaiting(WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    BacktestExperimentDtos.ExperimentSummary experiment;
    try {
      experiment = experiments.get(fold.experimentId);
    } catch (RuntimeException e) {
      return;
    }
    if (!Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(experiment.status()))
      return;
    WalkForwardSelectionMetric metric;
    try {
      metric = WalkForwardSelectionMetric.valueOf(study.selectionMetric);
    } catch (IllegalArgumentException e) {
      markFailed(fold, "WALK_FORWARD_REQUEST_INVALID", "selectionMetric is invalid");
      return;
    }
    WalkForwardTrainingCandidateRow selected =
        folds.findBestTrainingCandidate(
            fold.experimentId,
            metric.column(),
            metric.isAscending() ? "ASC" : "DESC",
            study.minimumTrainTrades);
    if (selected == null) {
      markFailed(
          fold, "WALK_FORWARD_NO_ELIGIBLE_CANDIDATE", "no eligible completed TRAIN candidate");
      return;
    }
    BacktestRunRow validation = runs.findByRunId(selected.validationRunId);
    if (validation == null || !"COMPLETED".equals(validation.status)) {
      markFailed(
          fold,
          "WALK_FORWARD_SELECTED_VALIDATION_FAILED",
          "selected validation run is not completed");
      return;
    }
    if (folds.completeSelection(
            fold.foldId,
            selected.candidateId,
            selected.parametersJson,
            selected.trainingRunId,
            selected.validationRunId,
            selected.metricValue,
            Instant.now())
        != 1)
      throw error(
          "WALK_FORWARD_STATE_CONFLICT",
          "complete selection affected an unexpected number of rows");
  }

  private BacktestExperimentCreateRequest request(
      WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    BacktestExperimentCreateRequest q = new BacktestExperimentCreateRequest();
    q.setDatasetId(study.datasetId);
    q.setStrategyCode(study.strategyCode);
    q.setStrategyVersion(study.strategyVersion);
    q.setParameterGrid(readGrid(study.parameterGridJson));
    q.setTrainingStartOpenTimeInclusive(Instant.ofEpochMilli(fold.trainingStartOpenTimeMs));
    q.setTrainingEndOpenTimeExclusive(Instant.ofEpochMilli(fold.trainingEndOpenTimeMs));
    q.setValidationStartOpenTimeInclusive(Instant.ofEpochMilli(fold.validationStartOpenTimeMs));
    q.setValidationEndOpenTimeExclusive(Instant.ofEpochMilli(fold.validationEndOpenTimeMs));
    q.setOrderAmount(study.orderAmount);
    q.setFeeRate(study.feeRate);
    q.setForceCloseAtEnd(study.forceCloseAtEnd);
    return q;
  }

  private Map<String, List<Integer>> readGrid(String value) {
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {});
    } catch (Exception e) {
      throw error("WALK_FORWARD_REQUEST_INVALID", "stored grid JSON is invalid");
    }
  }

  private void markFailed(WalkForwardFoldRow fold, String code, String message) {
    folds.markFailed(fold.foldId, code, clean(message), Instant.now());
  }

  private boolean isPermanent(RuntimeException e, String code) {
    return PERMANENT.contains(code);
  }

  private String code(RuntimeException e) {
    return e instanceof WalkForwardTaskException w
        ? w.getErrorCode()
        : e instanceof BacktestTaskException b ? b.getErrorCode() : "WALK_FORWARD_DISPATCH_FAILED";
  }

  private String message(RuntimeException e) {
    return clean(e.getMessage());
  }

  private String clean(String message) {
    String value =
        message == null ? "walk-forward dispatch failed" : message.replaceAll("[\\r\\n]", " ");
    return value.substring(0, Math.min(1000, value.length()));
  }

  private WalkForwardTaskException error(String code, String message) {
    return new WalkForwardTaskException(code, clean(message));
  }
}
