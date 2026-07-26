package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestExperimentAggregateTest {
  private static BacktestExperimentAggregate.RunState run(String status, String progress) {
    return new BacktestExperimentAggregate.RunState(
        true, status, progress == null ? null : new BigDecimal(progress));
  }

  private static BacktestExperimentAggregate.RunState missing() {
    return new BacktestExperimentAggregate.RunState(false, null, BigDecimal.ZERO);
  }

  @Test
  void aggregatesQueuedRunningAndCompletedStates() {
    var queued =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState("PENDING", missing(), missing())));
    assertEquals("QUEUED", queued.status());
    // A pending candidate itself is not counted as active.
    assertEquals(1, queued.pendingCandidates());
    var running =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "DISPATCHED", run("RUNNING", "42.345"), run("QUEUED", "10"))));
    assertEquals("RUNNING", running.status());
    assertEquals(1, running.activeCandidates());
    assertEquals(new BigDecimal("26.17"), running.progressPercent());
    var complete =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "DISPATCHED", run("COMPLETED", "100"), run("COMPLETED", "100"))));
    assertEquals("COMPLETED", complete.status());
    assertEquals(2, complete.completedLegs());
    assertEquals(100, complete.progressPercent().intValue());
  }

  @Test
  void distinguishesMixedFailuresAndAllFailures() {
    var mixed =
        BacktestExperimentAggregate.calculate(
            2,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "DISPATCHED", run("COMPLETED", "100"), run("COMPLETED", "100")),
                new BacktestExperimentAggregate.CandidateState("FAILED", missing(), missing())));
    assertEquals("COMPLETED_WITH_FAILURES", mixed.status());
    assertEquals(1, mixed.completedCandidates());
    assertEquals(1, mixed.failedCandidates());
    assertEquals(2, mixed.failedLegs());
    var allFailed =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "DISPATCHED", missing(), run("COMPLETED", "100"))));
    assertEquals("FAILED", allFailed.status());
    assertEquals(1, allFailed.failedCandidates());
    assertEquals(1, allFailed.failedLegs());
    assertEquals(100, allFailed.progressPercent().intValue());
  }

  @Test
  void failedDispatchWithRunningTrainingRemainsActiveUntilTrainingTerminates() {
    var running =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "FAILED", run("RUNNING", "42.00"), missing())));
    assertEquals("RUNNING", running.status());
    assertEquals(1, running.activeCandidates());
    assertEquals(1, running.failedCandidates());
    assertEquals(new BigDecimal("71.00"), running.progressPercent());
    var terminal =
        BacktestExperimentAggregate.calculate(
            1,
            List.of(
                new BacktestExperimentAggregate.CandidateState(
                    "FAILED", run("COMPLETED", "100"), missing())));
    assertEquals("FAILED", terminal.status());
    assertEquals(0, terminal.activeCandidates());
    assertEquals(0, new BigDecimal("100.00").compareTo(terminal.progressPercent()));
  }
}
