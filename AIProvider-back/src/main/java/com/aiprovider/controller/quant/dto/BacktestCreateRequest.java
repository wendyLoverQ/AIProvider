package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class BacktestCreateRequest {
    private long datasetId;
    private Instant startOpenTimeInclusive, endOpenTimeExclusive;
    private String strategyCode, strategyVersion;
    private Map<String,Integer> strategyParameters;
    private BigDecimal orderAmount, feeRate;
    private boolean forceCloseAtEnd;
    public long getDatasetId(){return datasetId;} public void setDatasetId(long v){datasetId=v;}
    public Instant getStartOpenTimeInclusive(){return startOpenTimeInclusive;} public void setStartOpenTimeInclusive(Instant v){startOpenTimeInclusive=v;}
    public Instant getEndOpenTimeExclusive(){return endOpenTimeExclusive;} public void setEndOpenTimeExclusive(Instant v){endOpenTimeExclusive=v;}
    public String getStrategyCode(){return strategyCode;} public void setStrategyCode(String v){strategyCode=v;}
    public String getStrategyVersion(){return strategyVersion;} public void setStrategyVersion(String v){strategyVersion=v;}
    public Map<String,Integer> getStrategyParameters(){return strategyParameters;} public void setStrategyParameters(Map<String,Integer> v){strategyParameters=v;}
    public BigDecimal getOrderAmount(){return orderAmount;} public void setOrderAmount(BigDecimal v){orderAmount=v;}
    public BigDecimal getFeeRate(){return feeRate;} public void setFeeRate(BigDecimal v){feeRate=v;}
    public boolean isForceCloseAtEnd(){return forceCloseAtEnd;} public void setForceCloseAtEnd(boolean v){forceCloseAtEnd=v;}
}
