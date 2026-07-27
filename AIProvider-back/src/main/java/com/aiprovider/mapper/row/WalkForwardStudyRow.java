package com.aiprovider.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

public class WalkForwardStudyRow {
  public long id, datasetId;
  public String studyId, provider, marketType, dataType, symbol, intervalCode;
  public String strategyCode, strategyVersion, parameterGridJson, windowMode, selectionMetric;
  public String executionProfileCode, directionMode, orderSizingMode;
  public int trainingBars,
      validationBars,
      stepBars,
      foldCount,
      candidateCountPerFold,
      totalChildRuns;
  public int minimumTrainTrades;
  public long studyStartOpenTimeMs, studyEndOpenTimeMs;
  public BigDecimal orderAmount, feeRate, progressPercent;
  public Integer successfulOosFolds, failedFolds, oosTradeCount, parameterChanges;
  public Boolean hasOosGaps;
  public BigDecimal oosTotalReturnRatio, oosMaximumDrawdownRatio, oosTotalFees;
  public boolean forceCloseAtEnd;
  public String status, errorCode, errorMessage;
  public Instant createdAt, updatedAt, startedAt, finishedAt;
}
