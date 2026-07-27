package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestExperimentDispatcherTest {
  @Test
  void doesNotClaimWhenExperimentCapacityIsFull() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestExperimentService aggregate = mock(BacktestExperimentService.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow first = candidate(experiment, "c1", "t1", "v1");
    BacktestExperimentCandidateRow second = candidate(experiment, "c2", "t2", "v2");
    BacktestRunMapper runs = mock(BacktestRunMapper.class);
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of(first, second));
    when(runs.findByRunIds(anyList()))
        .thenReturn(List.of(run("t1"), run("v1"), run("t2"), run("v2")));
    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            runs,
            mock(BacktestRunService.class),
            aggregate,
            properties(2),
            new ObjectMapper())
        .tick();
    verify(candidates, never()).claimNextPending(anyString(), anyString(), any());
  }

  @Test
  void usesFixedIdsAndMarksCandidateOnlyAfterBothRunsAreCreated() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestRunService runs = mock(BacktestRunService.class);
    BacktestExperimentService aggregate = mock(BacktestExperimentService.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow candidate = new BacktestExperimentCandidateRow();
    candidate.candidateId = "c";
    candidate.experimentId = experiment.experimentId;
    candidate.parametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
    candidate.trainingRunId = "t";
    candidate.validationRunId = "v";
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(candidate);
    when(candidates.markDispatched(anyString(), anyString(), any())).thenReturn(1);
    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            runs,
            aggregate,
            properties(1),
            new ObjectMapper())
        .tick();
    verify(runs).createWithRunId(eq("t"), any());
    verify(runs).createWithRunId(eq("v"), any());
    verify(candidates).markDispatched(eq("c"), anyString(), any());
  }

  @Test
  void aConcurrentCasMissStopsTheTick() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    when(experiments.findNonTerminal()).thenReturn(List.of(row()));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(0);
    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            mock(BacktestRunService.class),
            mock(BacktestExperimentService.class),
            properties(2),
            new ObjectMapper())
        .tick();
    verify(candidates).claimNextPending(anyString(), anyString(), any());
    verify(candidates, never()).findClaimed(anyString(), anyString());
    verify(candidates, times(1)).resetStaleClaims(any(), any());
  }

  @Test
  void queueFullReleasesClaimForTheNextTick() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestRunService runs = mock(BacktestRunService.class);
    BacktestExperimentService aggregate = mock(BacktestExperimentService.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow candidate = candidate(experiment, "c", "t", "v");
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1, 0);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(candidate);
    when(candidates.releaseClaimToPending(anyString(), anyString(), any())).thenReturn(1);
    doThrow(new BacktestTaskException("BACKTEST_QUEUE_FULL", "queue is full"))
        .when(runs)
        .createWithRunId(eq("v"), any());

    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            runs,
            aggregate,
            properties(1),
            new ObjectMapper())
        .tick();

    verify(candidates).releaseClaimToPending(eq("c"), anyString(), any());
    verify(candidates, never()).markDispatchFailed(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void malformedParametersArePermanentAndDoNotReleaseClaim() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow candidate = candidate(experiment, "c", "t", "v");
    candidate.parametersJson = "{invalid";
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1, 0);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(candidate);
    when(candidates.markDispatchFailed(anyString(), anyString(), any(), any(), any()))
        .thenReturn(1);

    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            mock(BacktestRunService.class),
            mock(BacktestExperimentService.class),
            properties(1),
            new ObjectMapper())
        .tick();

    verify(candidates)
        .markDispatchFailed(
            eq("c"), anyString(), eq("BACKTEST_EXPERIMENT_DISPATCH_FAILED"), anyString(), any());
    verify(candidates, never()).releaseClaimToPending(anyString(), anyString(), any());
  }

  @Test
  void releaseCasMissDoesNotMarkCandidateFailed() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestRunService runs = mock(BacktestRunService.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow candidate = candidate(experiment, "c", "t", "v");
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1, 0);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(candidate);
    when(candidates.releaseClaimToPending(anyString(), anyString(), any())).thenReturn(0);
    doThrow(new BacktestTaskException("BACKTEST_QUEUE_FULL", "queue is full"))
        .when(runs)
        .createWithRunId(eq("v"), any());

    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            runs,
            mock(BacktestExperimentService.class),
            properties(1),
            new ObjectMapper())
        .tick();

    verify(candidates).releaseClaimToPending(eq("c"), anyString(), any());
    verify(candidates, never()).markDispatchFailed(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void temporaryFailureInOneExperimentDoesNotStopTheNextExperiment() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestRunService runs = mock(BacktestRunService.class);
    BacktestExperimentRow firstExperiment = row();
    BacktestExperimentRow secondExperiment = row();
    BacktestExperimentCandidateRow first = candidate(firstExperiment, "c1", "t1", "v1");
    BacktestExperimentCandidateRow second = candidate(secondExperiment, "c2", "t2", "v2");
    when(experiments.findNonTerminal()).thenReturn(List.of(firstExperiment, secondExperiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1, 1);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(first, second);
    when(candidates.releaseClaimToPending(anyString(), anyString(), any())).thenReturn(1);
    when(candidates.markDispatched(anyString(), anyString(), any())).thenReturn(1);
    when(runs.createWithRunId(eq("t1"), any())).thenReturn("t1");
    when(runs.createWithRunId(eq("t2"), any())).thenReturn("t2");
    when(runs.createWithRunId(eq("v1"), any()))
        .thenThrow(new BacktestTaskException("BACKTEST_QUEUE_FULL", "queue is full"));
    when(runs.createWithRunId(eq("v2"), any())).thenReturn("v2");

    new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            runs,
            mock(BacktestExperimentService.class),
            properties(1),
            new ObjectMapper())
        .tick();

    verify(candidates).releaseClaimToPending(eq("c1"), anyString(), any());
    verify(candidates).markDispatched(eq("c2"), anyString(), any());
  }

  @Test
  void nextTickRetriesValidationWithoutRecreatingTrainingContract() {
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestRunService runs = mock(BacktestRunService.class);
    BacktestExperimentRow experiment = row();
    BacktestExperimentCandidateRow candidate = candidate(experiment, "c", "t", "v");
    when(experiments.findNonTerminal()).thenReturn(List.of(experiment), List.of(experiment));
    when(candidates.findAll(anyString())).thenReturn(List.of());
    when(candidates.claimNextPending(anyString(), anyString(), any())).thenReturn(1, 1);
    when(candidates.findClaimed(anyString(), anyString())).thenReturn(candidate);
    when(candidates.releaseClaimToPending(anyString(), anyString(), any())).thenReturn(1);
    when(candidates.markDispatched(anyString(), anyString(), any())).thenReturn(1);
    when(runs.createWithRunId(eq("t"), any())).thenReturn("t");
    when(runs.createWithRunId(eq("v"), any()))
        .thenThrow(new BacktestTaskException("BACKTEST_QUEUE_FULL", "queue is full"))
        .thenReturn("v");

    BacktestExperimentDispatcher dispatcher =
        new BacktestExperimentDispatcher(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            runs,
            mock(BacktestExperimentService.class),
            properties(1),
            new ObjectMapper());
    dispatcher.tick();
    dispatcher.tick();

    verify(runs, times(2)).createWithRunId(eq("t"), any());
    verify(runs, times(2)).createWithRunId(eq("v"), any());
    verify(candidates).markDispatched(eq("c"), anyString(), any());
  }

  private QuantExperimentProperties properties(int active) {
    QuantExperimentProperties p = new QuantExperimentProperties();
    p.setMaxActiveCandidatesPerExperiment(active);
    return p;
  }

  private BacktestExperimentCandidateRow candidate(
      BacktestExperimentRow experiment, String id, String train, String validation) {
    BacktestExperimentCandidateRow c = new BacktestExperimentCandidateRow();
    c.candidateId = id;
    c.experimentId = experiment.experimentId;
    c.parametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
    c.trainingRunId = train;
    c.validationRunId = validation;
    c.dispatchStatus = "DISPATCHED";
    return c;
  }

  private BacktestRunRow run(String id) {
    BacktestRunRow run = new BacktestRunRow();
    run.runId = id;
    run.status = "RUNNING";
    run.progressPercent = java.math.BigDecimal.TEN;
    return run;
  }

  private BacktestExperimentRow row() {
    BacktestExperimentRow row = new BacktestExperimentRow();
    row.experimentId = UUID.randomUUID().toString();
    row.datasetId = 1;
    row.strategyCode = "EMA_CROSS_LONG_ONLY";
    row.strategyVersion = "1.0.0";
    row.executionProfileCode = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.trainingStartOpenTimeMs = 0;
    row.trainingEndOpenTimeMs = 60000;
    row.validationStartOpenTimeMs = 60000;
    row.validationEndOpenTimeMs = 120000;
    row.orderAmount = java.math.BigDecimal.ONE;
    row.feeRate = new java.math.BigDecimal("0.001");
    return row;
  }
}
