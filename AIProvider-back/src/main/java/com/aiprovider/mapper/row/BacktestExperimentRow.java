package com.aiprovider.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

public class BacktestExperimentRow {
    public long id, datasetId;
    public String experimentId, provider, marketType, dataType, symbol, intervalCode, strategyCode, strategyVersion;
    public String executionProfileCode, directionMode, orderSizingMode;
    public String parameterGridJson, status, errorCode, errorMessage;
    public int candidateCount;
    public long trainingStartOpenTimeMs, trainingEndOpenTimeMs, validationStartOpenTimeMs, validationEndOpenTimeMs;
    public BigDecimal orderAmount, feeRate, progressPercent;
    public boolean forceCloseAtEnd;
    public Instant createdAt, updatedAt, startedAt, finishedAt;
}
