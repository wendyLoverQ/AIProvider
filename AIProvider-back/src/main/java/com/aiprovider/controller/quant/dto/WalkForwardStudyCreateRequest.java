package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;

public class WalkForwardStudyCreateRequest {
  private long datasetId;
  private String strategyCode, strategyVersion;
  @NotBlank private String executionProfileCode;
  @NotBlank private String directionMode;
  @NotBlank private String orderSizingMode;
  private Map<String, List<Integer>> parameterGrid;
  private Instant studyStartOpenTimeInclusive, studyEndOpenTimeExclusive;
  private int trainingBars, validationBars, minimumTrainTrades;
  private String selectionMetric;
  private BigDecimal orderAmount, feeRate;
  private boolean forceCloseAtEnd;

  public long getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(long v) {
    datasetId = v;
  }

  public String getStrategyCode() {
    return strategyCode;
  }

  public void setStrategyCode(String v) {
    strategyCode = v;
  }

  public String getStrategyVersion() {
    return strategyVersion;
  }

  public void setStrategyVersion(String v) {
    strategyVersion = v;
  }

  public String getExecutionProfileCode() { return executionProfileCode; }
  public void setExecutionProfileCode(String v) { executionProfileCode = v; }
  public String getDirectionMode() { return directionMode; }
  public void setDirectionMode(String v) { directionMode = v; }
  public String getOrderSizingMode() { return orderSizingMode; }
  public void setOrderSizingMode(String v) { orderSizingMode = v; }

  public Map<String, List<Integer>> getParameterGrid() {
    return parameterGrid;
  }

  public void setParameterGrid(Map<String, List<Integer>> v) {
    parameterGrid = v;
  }

  public Instant getStudyStartOpenTimeInclusive() {
    return studyStartOpenTimeInclusive;
  }

  public void setStudyStartOpenTimeInclusive(Instant v) {
    studyStartOpenTimeInclusive = v;
  }

  public Instant getStudyEndOpenTimeExclusive() {
    return studyEndOpenTimeExclusive;
  }

  public void setStudyEndOpenTimeExclusive(Instant v) {
    studyEndOpenTimeExclusive = v;
  }

  public int getTrainingBars() {
    return trainingBars;
  }

  public void setTrainingBars(int v) {
    trainingBars = v;
  }

  public int getValidationBars() {
    return validationBars;
  }

  public void setValidationBars(int v) {
    validationBars = v;
  }

  public String getSelectionMetric() {
    return selectionMetric;
  }

  public void setSelectionMetric(String v) {
    selectionMetric = v;
  }

  public int getMinimumTrainTrades() {
    return minimumTrainTrades;
  }

  public void setMinimumTrainTrades(int v) {
    minimumTrainTrades = v;
  }

  public BigDecimal getOrderAmount() {
    return orderAmount;
  }

  public void setOrderAmount(BigDecimal v) {
    orderAmount = v;
  }

  public BigDecimal getFeeRate() {
    return feeRate;
  }

  public void setFeeRate(BigDecimal v) {
    feeRate = v;
  }

  public boolean isForceCloseAtEnd() {
    return forceCloseAtEnd;
  }

  public void setForceCloseAtEnd(boolean v) {
    forceCloseAtEnd = v;
  }
}
