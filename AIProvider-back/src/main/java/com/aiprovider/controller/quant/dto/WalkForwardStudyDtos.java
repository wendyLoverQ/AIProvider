package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WalkForwardStudyDtos {
  private WalkForwardStudyDtos() {}

  public record CreateResponse(
      String studyId, int foldCount, int candidateCountPerFold, int totalChildRuns) {}

  public record StudySummary(
      String studyId,
      long datasetId,
      String provider,
      String marketType,
      String dataType,
      String symbol,
      String intervalCode,
      String strategyCode,
      String strategyVersion,
      String executionProfileCode,
      String directionMode,
      String orderSizingMode,
      Map<String, List<Integer>> parameterGrid,
      String windowMode,
      Instant studyStartOpenTimeInclusive,
      Instant studyEndOpenTimeExclusive,
      int trainingBars,
      int validationBars,
      int stepBars,
      int foldCount,
      int candidateCountPerFold,
      int totalChildRuns,
      String selectionMetric,
      int minimumTrainTrades,
      BigDecimal orderAmount,
      BigDecimal feeRate,
      boolean forceCloseAtEnd,
      String status,
      BigDecimal progressPercent,
      int pendingFolds,
      int activeFolds,
      int completedFolds,
      int failedFolds,
      Integer selectedParameterChanges,
      Integer successfulOosFolds,
      Boolean hasOosGaps,
      Integer totalOosTradeCount,
      BigDecimal totalOosFees,
      BigDecimal totalOosReturnRatio,
      String errorCode,
      String errorMessage,
      Instant createdAt,
      Instant startedAt,
      Instant finishedAt,
      Instant updatedAt) {}

  public record ParameterSelectionFrequency(
      Map<String, Integer> parameters, int selectedCount, int firstFoldIndex, int lastFoldIndex) {}

  public record StudyDetail(
      StudySummary summary, List<ParameterSelectionFrequency> parameterFrequencies) {}

  public record FoldResult(
      String foldId,
      int foldIndex,
      Instant trainingStartOpenTimeInclusive,
      Instant trainingEndOpenTimeExclusive,
      Instant validationStartOpenTimeInclusive,
      Instant validationEndOpenTimeExclusive,
      String experimentId,
      String status,
      BigDecimal progressPercent,
      String experimentStatus,
      String selectedCandidateId,
      Map<String, Integer> selectedParameters,
      String selectedTrainingRunId,
      String selectedValidationRunId,
      BigDecimal selectionMetricValue,
      BacktestDtos.Metrics trainingMetrics,
      BacktestDtos.Metrics validationMetrics,
      String errorCode,
      String errorMessage,
      Instant startedAt,
      Instant finishedAt,
      Instant updatedAt) {}

  public record OosPoint(
      int pointIndex,
      int foldIndex,
      Instant openTime,
      BigDecimal indexRatio,
      BigDecimal drawdownRatio) {}

  public record OosEquity(
      boolean sampled,
      int totalPoints,
      int successfulFolds,
      int missingFolds,
      boolean hasGaps,
      BigDecimal totalReturnRatio,
      BigDecimal maximumDrawdownRatio,
      List<OosPoint> points) {}
}
