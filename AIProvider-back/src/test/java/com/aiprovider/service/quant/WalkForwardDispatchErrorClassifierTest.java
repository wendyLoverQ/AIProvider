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

  @Test
  void executionCompatibilityFailuresArePermanent() {
    for (String code :
        new String[] {
          "BACKTEST_EXECUTION_PROFILE_REQUIRED",
          "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED",
          "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE",
          "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE",
          "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
          "BACKTEST_DIRECTION_INCOMPATIBLE",
          "BACKTEST_ORDER_SIZING_INCOMPATIBLE",
          "BACKTEST_MARKET_FEATURE_MISSING"
        }) {
      WalkForwardDispatchErrorClassifier.Classification result =
          WalkForwardDispatchErrorClassifier.classify(new BacktestTaskException(code, "invalid"));
      assertEquals(WalkForwardDispatchErrorClassifier.Kind.PERMANENT, result.kind(), code);
      assertEquals(code, result.errorCode());
    }
  }
}
