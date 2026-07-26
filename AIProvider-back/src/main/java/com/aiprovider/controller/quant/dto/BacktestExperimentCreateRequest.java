package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class BacktestExperimentCreateRequest {
    private long datasetId;
    private String strategyCode, strategyVersion;
    private Map<String, List<Integer>> parameterGrid;
    private Instant trainingStartOpenTimeInclusive, trainingEndOpenTimeExclusive,
            validationStartOpenTimeInclusive, validationEndOpenTimeExclusive;
    private BigDecimal orderAmount, feeRate;
    private boolean forceCloseAtEnd;
    public long getDatasetId(){return datasetId;} public void setDatasetId(long v){datasetId=v;}
    public String getStrategyCode(){return strategyCode;} public void setStrategyCode(String v){strategyCode=v;}
    public String getStrategyVersion(){return strategyVersion;} public void setStrategyVersion(String v){strategyVersion=v;}
    public Map<String,List<Integer>> getParameterGrid(){return parameterGrid;} public void setParameterGrid(Map<String,List<Integer>> v){parameterGrid=v;}
    public Instant getTrainingStartOpenTimeInclusive(){return trainingStartOpenTimeInclusive;} public void setTrainingStartOpenTimeInclusive(Instant v){trainingStartOpenTimeInclusive=v;}
    public Instant getTrainingEndOpenTimeExclusive(){return trainingEndOpenTimeExclusive;} public void setTrainingEndOpenTimeExclusive(Instant v){trainingEndOpenTimeExclusive=v;}
    public Instant getValidationStartOpenTimeInclusive(){return validationStartOpenTimeInclusive;} public void setValidationStartOpenTimeInclusive(Instant v){validationStartOpenTimeInclusive=v;}
    public Instant getValidationEndOpenTimeExclusive(){return validationEndOpenTimeExclusive;} public void setValidationEndOpenTimeExclusive(Instant v){validationEndOpenTimeExclusive=v;}
    public BigDecimal getOrderAmount(){return orderAmount;} public void setOrderAmount(BigDecimal v){orderAmount=v;}
    public BigDecimal getFeeRate(){return feeRate;} public void setFeeRate(BigDecimal v){feeRate=v;}
    public boolean isForceCloseAtEnd(){return forceCloseAtEnd;} public void setForceCloseAtEnd(boolean v){forceCloseAtEnd=v;}
}
