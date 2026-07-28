package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import com.aiprovider.mapper.BacktestExperimentMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BacktestExperimentDispatcher {
  private static final Logger log = LogManager.getLogger(BacktestExperimentDispatcher.class);
  private static final Set<String> PERMANENT_CODES =
      Set.of(
          "BACKTEST_REQUEST_INVALID",
          "BACKTEST_DATASET_NOT_FOUND",
          "BACKTEST_STRATEGY_NOT_FOUND",
          "BACKTEST_PARAMETER_INVALID",
          "BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED",
          "BACKTEST_EXECUTION_PROFILE_REQUIRED",
          "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED",
          "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE",
          "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE",
          "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
          "BACKTEST_DIRECTION_INCOMPATIBLE",
          "BACKTEST_ORDER_SIZING_INCOMPATIBLE",
          "BACKTEST_MARKET_FEATURE_MISSING",
          "BACKTEST_RUN_ID_CONFLICT",
          "BACKTEST_EXPERIMENT_GRID_INVALID",
          "BACKTEST_EXPERIMENT_RANGE_INVALID",
          "BACKTEST_EXPERIMENT_DISPATCH_FAILED");

  private final BacktestExperimentMapper experiments;
  private final BacktestExperimentCandidateMapper candidates;
  private final BacktestRunMapper runs;
  private final BacktestRunService runService;
  private final BacktestExperimentService aggregateService;
  private final QuantExperimentProperties properties;
  private final ObjectMapper json;

  public BacktestExperimentDispatcher(
      BacktestExperimentMapper experiments,
      BacktestExperimentCandidateMapper candidates,
      BacktestRunMapper runs,
      BacktestRunService runService,
      BacktestExperimentService aggregateService,
      QuantExperimentProperties properties,
      ObjectMapper json) {
    this.experiments = experiments;
    this.candidates = candidates;
    this.runs = runs;
    this.runService = runService;
    this.aggregateService = aggregateService;
    this.properties = properties;
    this.json = json;
  }

  @Scheduled(fixedDelayString = "${quant.experiment.dispatcher-fixed-delay-ms:2000}")
  public void tick() {
    Instant now = Instant.now();
    try {
      candidates.resetStaleClaims(now.minusSeconds(properties.getStaleClaimSeconds()), now);
    } catch (RuntimeException exception) {
      log.warn("quant experiment stale claim reset failed", exception);
    }
    for (BacktestExperimentRow experiment : experiments.findNonTerminal()) {
      try {
        dispatchExperiment(experiment);
      } catch (RuntimeException exception) {
        log.error(
            "quant experiment tick failed experimentId={}", experiment.experimentId, exception);
      }
    }
  }

  private void dispatchExperiment(BacktestExperimentRow experiment) {
    refresh(experiment);
    int active = activeCandidates(experiment);
    while (active < properties.getMaxActiveCandidatesPerExperiment()) {
      String token = UUID.randomUUID().toString();
      if (candidates.claimNextPending(experiment.experimentId, token, Instant.now()) != 1) {
        break;
      }
      BacktestExperimentCandidateRow candidate =
          candidates.findClaimed(experiment.experimentId, token);
      if (candidate == null) {
        log.error(
            "quant experiment claim returned no candidate experimentId={} token={}",
            experiment.experimentId,
            token);
        break;
      }
      dispatchCandidate(experiment, candidate, token);
      active = Math.max(active + 1, activeCandidates(experiment));
    }
    refresh(experiment);
  }

  private void dispatchCandidate(
      BacktestExperimentRow experiment, BacktestExperimentCandidateRow candidate, String token) {
    try {
      runService.createWithRunId(
          candidate.trainingRunId,
          request(
              experiment,
              candidate.parametersJson,
              experiment.trainingStartOpenTimeMs,
              experiment.trainingEndOpenTimeMs));
      runService.createWithRunId(
          candidate.validationRunId,
          request(
              experiment,
              candidate.parametersJson,
              experiment.validationStartOpenTimeMs,
              experiment.validationEndOpenTimeMs));
      if (candidates.markDispatched(candidate.candidateId, token, Instant.now()) != 1) {
        throw new BacktestTaskException(
            "BACKTEST_EXPERIMENT_STATE_CONFLICT", "candidate dispatch CAS lost");
      }
    } catch (RuntimeException exception) {
      String code = errorCode(exception);
      if (isPermanent(exception, code)) {
        if (candidates.markDispatchFailed(
                candidate.candidateId, token, code, message(exception), Instant.now())
            != 1) {
          log.error(
              "quant experiment dispatch failure CAS lost candidateId={} token={}",
              candidate.candidateId,
              token);
        }
      } else {
        int affected =
            candidates.releaseClaimToPending(candidate.candidateId, token, Instant.now());
        if (affected == 0) {
          log.error(
              "quant experiment transient dispatch release CAS lost candidateId={} token={}",
              candidate.candidateId,
              token);
        } else if (affected > 1) {
          throw new BacktestTaskException(
              "BACKTEST_EXPERIMENT_STATE_CONFLICT", "claim release affected multiple rows");
        } else {
          log.warn(
              "quant experiment transient dispatch released candidateId={} code={}",
              candidate.candidateId,
              code,
              exception);
        }
      }
    }
  }

  private int activeCandidates(BacktestExperimentRow experiment) {
    BacktestExperimentSnapshot snapshot =
        BacktestExperimentSnapshot.load(experiment, candidates, runs);
    return BacktestExperimentAggregate.calculate(
            experiment.candidateCount,
            snapshot.candidates().stream()
                .map(
                    candidate ->
                        new BacktestExperimentAggregate.CandidateState(
                            candidate.dispatchStatus,
                            state(snapshot.run(candidate.trainingRunId)),
                            state(snapshot.run(candidate.validationRunId))))
                .toList())
        .activeCandidates();
  }

  private void refresh(BacktestExperimentRow experiment) {
    aggregateService.get(experiment.experimentId);
  }

  private BacktestExperimentAggregate.RunState state(com.aiprovider.mapper.row.BacktestRunRow run) {
    return run == null
        ? new BacktestExperimentAggregate.RunState(false, null, null)
        : new BacktestExperimentAggregate.RunState(true, run.status, run.progressPercent);
  }

  private BacktestCreateRequest request(
      BacktestExperimentRow experiment, String parameters, long start, long end) {
    BacktestCreateRequest request = new BacktestCreateRequest();
    request.setDatasetId(experiment.datasetId);
    request.setStartOpenTimeInclusive(Instant.ofEpochMilli(start));
    request.setEndOpenTimeExclusive(Instant.ofEpochMilli(end));
    request.setStrategyCode(experiment.strategyCode);
    request.setStrategyVersion(experiment.strategyVersion);
    request.setExecutionProfileCode(experiment.executionProfileCode);
    request.setDirectionMode(experiment.directionMode);
    request.setOrderSizingMode(experiment.orderSizingMode);
    try {
      request.setStrategyParameters(
          json.readValue(parameters, new TypeReference<LinkedHashMap<String, Integer>>() {}));
    } catch (Exception exception) {
      throw new BacktestTaskException(
          "BACKTEST_EXPERIMENT_DISPATCH_FAILED", "candidate parameters are invalid");
    }
    request.setInitialCapital(experiment.initialCapital);
    request.setOrderAmount(experiment.orderAmount);
    request.setFeeRate(experiment.feeRate);
    request.setForceCloseAtEnd(true);
    return request;
  }

  private String errorCode(RuntimeException exception) {
    if (exception instanceof BacktestTaskException taskException) {
      return taskException.getErrorCode();
    }
    return "BACKTEST_EXPERIMENT_DISPATCH_FAILED";
  }

  private boolean isPermanent(RuntimeException exception, String code) {
    return exception instanceof BacktestTaskException && PERMANENT_CODES.contains(code);
  }

  private String message(RuntimeException exception) {
    String value =
        exception.getMessage() == null ? "candidate dispatch failed" : exception.getMessage();
    value = value.replaceAll("[\\r\\n]", " ");
    return value.substring(0, Math.min(1000, value.length()));
  }
}
