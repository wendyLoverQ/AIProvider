package com.aiprovider.service.quant;

import java.util.Set;
import org.springframework.dao.DataAccessException;

/** Single permanent/retryable policy for both experiment creation and waiting. */
final class WalkForwardDispatchErrorClassifier {
  private static final Set<String> PERMANENT =
      Set.of(
          "BACKTEST_EXPERIMENT_NOT_FOUND",
          "BACKTEST_PERSISTENCE_FAILED",
          "BACKTEST_EXPERIMENT_REQUEST_INVALID",
          "BACKTEST_EXPERIMENT_GRID_INVALID",
          "BACKTEST_EXPERIMENT_RANGE_INVALID",
          "BACKTEST_EXPERIMENT_STATE_CONFLICT",
          "BACKTEST_EXPERIMENT_DISPATCH_FAILED",
          "BACKTEST_EXECUTION_PROFILE_REQUIRED",
          "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED",
          "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE",
          "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE",
          "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
          "BACKTEST_DIRECTION_INCOMPATIBLE",
          "BACKTEST_ORDER_SIZING_INCOMPATIBLE",
          "BACKTEST_MARKET_FEATURE_MISSING",
          "WALK_FORWARD_EXPERIMENT_CONFLICT",
          "WALK_FORWARD_EXPERIMENT_NOT_FOUND",
          "WALK_FORWARD_EXPERIMENT_INVALID",
          "WALK_FORWARD_NO_ELIGIBLE_CANDIDATE",
          "WALK_FORWARD_SELECTED_VALIDATION_FAILED",
          "WALK_FORWARD_OOS_INVALID",
          "WALK_FORWARD_STATE_CONFLICT",
          "WALK_FORWARD_REQUEST_INVALID",
          "WALK_FORWARD_WINDOW_INVALID",
          "WALK_FORWARD_TOO_LARGE");

  private WalkForwardDispatchErrorClassifier() {}

  static Classification classify(Throwable failure) {
    String originalCode = code(failure);
    String code = mapCode(originalCode);
    boolean permanent = PERMANENT.contains(originalCode) || PERMANENT.contains(code);
    if (failure instanceof DataAccessException) permanent = false;
    return new Classification(permanent ? Kind.PERMANENT : Kind.RETRYABLE, code, clean(failure.getMessage()));
  }

  private static String code(Throwable failure) {
    if (failure instanceof WalkForwardTaskException task) return task.getErrorCode();
    if (failure instanceof BacktestTaskException task) return task.getErrorCode();
    return "WALK_FORWARD_DISPATCH_FAILED";
  }

  private static String mapCode(String code) {
    return switch (code) {
      case "BACKTEST_EXPERIMENT_NOT_FOUND" -> "WALK_FORWARD_EXPERIMENT_NOT_FOUND";
      case "BACKTEST_EXPERIMENT_REQUEST_INVALID",
          "BACKTEST_EXPERIMENT_GRID_INVALID",
          "BACKTEST_EXPERIMENT_RANGE_INVALID" -> "WALK_FORWARD_EXPERIMENT_INVALID";
      case "BACKTEST_EXPERIMENT_STATE_CONFLICT" -> "WALK_FORWARD_EXPERIMENT_CONFLICT";
      default -> code;
    };
  }

  private static String clean(String value) {
    String text = value == null || value.isBlank() ? "walk-forward dispatch failed" : value;
    text = text.replaceAll("[\\r\\n]", " ");
    return text.substring(0, Math.min(1000, text.length()));
  }

  enum Kind { PERMANENT, RETRYABLE }

  record Classification(Kind kind, String errorCode, String errorMessage) {}
}
