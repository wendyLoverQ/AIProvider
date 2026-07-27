package com.aiprovider.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

public class ResearchStudyRow {
  public long id, datasetId;
  public String researchStudyId, name, description;
  public String provider, marketType, dataType, symbol, intervalCode;
  public String strategyCode, strategyVersion, executionProfileCode, directionMode, orderSizingMode;
  public String evaluationMode, parameterSpaceMode, parameterSpaceJson, expandedParameterGridJson, selectionMetric;
  public int candidateCount, trainingBars, validationBars, minimumTrainTrades;
  public long studyStartOpenTimeMs, studyEndOpenTimeMs;
  public BigDecimal orderAmount, feeRate, progressPercent;
  public Integer successfulOosFolds, failedFolds, oosTradeCount, parameterChanges;
  public Boolean hasOosGaps;
  public BigDecimal oosTotalReturnRatio, oosMaximumDrawdownRatio, oosTotalFees;
  public boolean forceCloseAtEnd;
  public String comparisonGroupKey, walkForwardStudyId, status, errorCode, errorMessage;
  public Instant createdAt, startedAt, finishedAt, updatedAt;
}
