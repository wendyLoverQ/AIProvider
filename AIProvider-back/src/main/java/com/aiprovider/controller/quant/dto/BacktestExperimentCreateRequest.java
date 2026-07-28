package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

public class BacktestExperimentCreateRequest {
    private long datasetId;
    private String strategyCode, strategyVersion;
    @NotBlank private String executionProfileCode;
    @NotBlank private String directionMode;
    @NotBlank private String orderSizingMode;
    private Map<String, List<Integer>> parameterGrid;
    private Instant trainingStartOpenTimeInclusive, trainingEndOpenTimeExclusive,
            validationStartOpenTimeInclusive, validationEndOpenTimeExclusive;
    @NotNull @Digits(integer=20, fraction=18) @DecimalMin(value="0", inclusive=false)
    private BigDecimal initialCapital;
    private BigDecimal orderAmount, feeRate;
    private boolean forceCloseAtEnd;
    public long getDatasetId(){return datasetId;} public void setDatasetId(long v){datasetId=v;}
    public String getStrategyCode(){return strategyCode;} public void setStrategyCode(String v){strategyCode=v;}
    public String getStrategyVersion(){return strategyVersion;} public void setStrategyVersion(String v){strategyVersion=v;}
    public String getExecutionProfileCode(){return executionProfileCode;} public void setExecutionProfileCode(String v){executionProfileCode=v;}
    public String getDirectionMode(){return directionMode;} public void setDirectionMode(String v){directionMode=v;}
    public String getOrderSizingMode(){return orderSizingMode;} public void setOrderSizingMode(String v){orderSizingMode=v;}
    public Map<String,List<Integer>> getParameterGrid(){return parameterGrid;} public void setParameterGrid(Map<String,List<Integer>> v){parameterGrid=v;}
    public Instant getTrainingStartOpenTimeInclusive(){return trainingStartOpenTimeInclusive;} public void setTrainingStartOpenTimeInclusive(Instant v){trainingStartOpenTimeInclusive=v;}
    public Instant getTrainingEndOpenTimeExclusive(){return trainingEndOpenTimeExclusive;} public void setTrainingEndOpenTimeExclusive(Instant v){trainingEndOpenTimeExclusive=v;}
    public Instant getValidationStartOpenTimeInclusive(){return validationStartOpenTimeInclusive;} public void setValidationStartOpenTimeInclusive(Instant v){validationStartOpenTimeInclusive=v;}
    public Instant getValidationEndOpenTimeExclusive(){return validationEndOpenTimeExclusive;} public void setValidationEndOpenTimeExclusive(Instant v){validationEndOpenTimeExclusive=v;}
    public BigDecimal getInitialCapital(){return initialCapital;} public void setInitialCapital(BigDecimal v){initialCapital=v;}
    public BigDecimal getOrderAmount(){return orderAmount;} public void setOrderAmount(BigDecimal v){orderAmount=v;}
    public BigDecimal getFeeRate(){return feeRate;} public void setFeeRate(BigDecimal v){feeRate=v;}
    public boolean isForceCloseAtEnd(){return forceCloseAtEnd;} public void setForceCloseAtEnd(boolean v){forceCloseAtEnd=v;}
}
