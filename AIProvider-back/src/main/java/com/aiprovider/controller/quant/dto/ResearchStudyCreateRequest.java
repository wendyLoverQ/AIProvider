package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ResearchStudyCreateRequest {
  private String name, description, strategyCode, strategyVersion, executionProfileCode, directionMode, orderSizingMode;
  private long datasetId;
  private String evaluationMode, parameterSpaceMode;
  private Map<String, IntegerRangeRequest> parameterSpace;
  private Instant studyStartOpenTimeInclusive, studyEndOpenTimeExclusive;
  private int trainingBars, validationBars, minimumTrainTrades;
  private String selectionMetric;
  private BigDecimal orderAmount, feeRate;
  private boolean forceCloseAtEnd;

  public String getName() { return name; }
  public void setName(String value) { name = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public long getDatasetId() { return datasetId; }
  public void setDatasetId(long value) { datasetId = value; }
  public String getStrategyCode() { return strategyCode; }
  public void setStrategyCode(String value) { strategyCode = value; }
  public String getStrategyVersion() { return strategyVersion; }
  public void setStrategyVersion(String value) { strategyVersion = value; }
  public String getExecutionProfileCode() { return executionProfileCode; }
  public void setExecutionProfileCode(String value) { executionProfileCode = value; }
  public String getDirectionMode() { return directionMode; }
  public void setDirectionMode(String value) { directionMode = value; }
  public String getOrderSizingMode() { return orderSizingMode; }
  public void setOrderSizingMode(String value) { orderSizingMode = value; }
  public String getEvaluationMode() { return evaluationMode; }
  public void setEvaluationMode(String value) { evaluationMode = value; }
  public String getParameterSpaceMode() { return parameterSpaceMode; }
  public void setParameterSpaceMode(String value) { parameterSpaceMode = value; }
  public Map<String, IntegerRangeRequest> getParameterSpace() { return parameterSpace; }
  public void setParameterSpace(Map<String, IntegerRangeRequest> value) { parameterSpace = value; }
  public Instant getStudyStartOpenTimeInclusive() { return studyStartOpenTimeInclusive; }
  public void setStudyStartOpenTimeInclusive(Instant value) { studyStartOpenTimeInclusive = value; }
  public Instant getStudyEndOpenTimeExclusive() { return studyEndOpenTimeExclusive; }
  public void setStudyEndOpenTimeExclusive(Instant value) { studyEndOpenTimeExclusive = value; }
  public int getTrainingBars() { return trainingBars; }
  public void setTrainingBars(int value) { trainingBars = value; }
  public int getValidationBars() { return validationBars; }
  public void setValidationBars(int value) { validationBars = value; }
  public String getSelectionMetric() { return selectionMetric; }
  public void setSelectionMetric(String value) { selectionMetric = value; }
  public int getMinimumTrainTrades() { return minimumTrainTrades; }
  public void setMinimumTrainTrades(int value) { minimumTrainTrades = value; }
  public BigDecimal getOrderAmount() { return orderAmount; }
  public void setOrderAmount(BigDecimal value) { orderAmount = value; }
  public BigDecimal getFeeRate() { return feeRate; }
  public void setFeeRate(BigDecimal value) { feeRate = value; }
  public boolean isForceCloseAtEnd() { return forceCloseAtEnd; }
  public void setForceCloseAtEnd(boolean value) { forceCloseAtEnd = value; }

  public static class IntegerRangeRequest {
    private int minimum, maximum, step;
    public int getMinimum() { return minimum; }
    public void setMinimum(int value) { minimum = value; }
    public int getMaximum() { return maximum; }
    public void setMaximum(int value) { maximum = value; }
    public int getStep() { return step; }
    public void setStep(int value) { step = value; }
  }
}
