package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import javax.validation.constraints.*;

public class BacktestCreateRequest {
    @Positive(message="datasetId 必须大于 0") private long datasetId;
    @NotNull(message="startOpenTimeInclusive 不能为空")
    private Instant startOpenTimeInclusive;
    @NotNull(message="endOpenTimeExclusive 不能为空") private Instant endOpenTimeExclusive;
    @NotBlank(message="strategyCode 不能为空") private String strategyCode;
    @NotBlank(message="strategyVersion 不能为空") private String strategyVersion;
    private Map<String,Integer> strategyParameters;
    @NotNull @DecimalMin(value="0.000000000000000001", inclusive=true) private BigDecimal orderAmount;
    @NotNull @DecimalMin(value="0", inclusive=true) @DecimalMax(value="0.01", inclusive=true) private BigDecimal feeRate;
    private boolean forceCloseAtEnd;
    @AssertTrue(message="forceCloseAtEnd 必须为 true") public boolean isForceCloseAtEndValid(){return forceCloseAtEnd;}
    @AssertTrue(message="strategyParameters 不得包含 null key/value") public boolean areStrategyParametersValid(){return strategyParameters==null||strategyParameters.entrySet().stream().noneMatch(e->e.getKey()==null||e.getValue()==null);}
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
