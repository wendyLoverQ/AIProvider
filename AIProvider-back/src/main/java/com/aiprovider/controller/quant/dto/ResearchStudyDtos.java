package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ResearchStudyDtos {
  private ResearchStudyDtos() {}
  public record CreateResponse(String researchStudyId, String walkForwardStudyId, int candidateCount) {}
  public record Summary(String researchStudyId, String name, String description, long datasetId,
      String provider, String marketType, String dataType, String symbol, String interval,
      String strategyCode, String strategyVersion, String executionProfileCode, String directionMode,
      String orderSizingMode, String evaluationMode, String parameterSpaceMode, int candidateCount,
      String comparisonGroupKey, String walkForwardStudyId, String status, BigDecimal progressPercent,
      int successfulOosFolds, int failedFolds, Boolean hasOosGaps, BigDecimal oosTotalReturnRatio,
      BigDecimal oosMaximumDrawdownRatio, Integer oosTradeCount, BigDecimal oosTotalFees,
      Integer parameterChanges, String errorCode, String errorMessage, Instant createdAt,
      Instant startedAt, Instant finishedAt, Instant updatedAt) {}
  public record Detail(Summary summary, Map<String, IntegerRange> parameterSpace,
      Map<String, List<Integer>> expandedParameterGrid, Instant studyStartOpenTimeInclusive,
      Instant studyEndOpenTimeExclusive, int trainingBars, int validationBars, String selectionMetric,
      int minimumTrainTrades, BigDecimal orderAmount, BigDecimal feeRate, boolean forceCloseAtEnd) {}
  public record IntegerRange(int minimum, int maximum, int step) {}
  public record Result(Summary summary) {}
}
