package com.aiprovider.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

/** Explicit database row for q_backtest_run; database types are kept visible. */
public class BacktestRunRow {
    public long id, datasetId; public String runId, datasetLastSyncTaskId, provider, marketType, dataType, symbol, intervalCode;
    public long startOpenTimeMs, endOpenTimeExclusiveMs; public String strategyCode, strategyVersion, requestedParametersJson, resolvedParametersJson;
    public BigDecimal orderAmount, feeRate, progressPercent, winRate, grossProfit, grossLoss, netProfit, totalReturnRatio, maximumDrawdownRatio, profitFactor, averageTradeReturnRatio, buyAndHoldReturnRatio, totalFees;
    public String executionProfileCode, directionMode, orderSizingMode;
    public boolean forceCloseAtEnd; public String status, executionModel, warningsJson, errorCode, errorMessage; public Integer barCount, tradeCount, winningTradeCount, losingTradeCount, breakEvenTradeCount, equityPointCount;
    public Instant datasetLastValidatedAt, queuedAt, startedAt, finishedAt, updatedAt;
}
