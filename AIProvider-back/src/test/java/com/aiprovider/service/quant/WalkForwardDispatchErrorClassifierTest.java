package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WalkForwardDispatchErrorClassifierTest {
  @Test
  void permanentExperimentErrorsBecomeStableWalkForwardErrors() {
    WalkForwardDispatchErrorClassifier.Classification result =
        WalkForwardDispatchErrorClassifier.classify(
            new BacktestTaskException("BACKTEST_EXPERIMENT_NOT_FOUND", "missing"));
    assertEquals(WalkForwardDispatchErrorClassifier.Kind.PERMANENT, result.kind());
    assertEquals("WALK_FORWARD_EXPERIMENT_NOT_FOUND", result.errorCode());
  }

  @Test
  void infrastructureFailureRemainsRetryable() {
    WalkForwardDispatchErrorClassifier.Classification result =
        WalkForwardDispatchErrorClassifier.classify(
            new DataAccessResourceFailureException("database unavailable", new SQLException("down")));
    assertEquals(WalkForwardDispatchErrorClassifier.Kind.RETRYABLE, result.kind());
    assertEquals("WALK_FORWARD_DISPATCH_FAILED", result.errorCode());
  }
}
