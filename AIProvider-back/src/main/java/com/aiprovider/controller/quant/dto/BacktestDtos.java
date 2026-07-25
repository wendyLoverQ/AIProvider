package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BacktestDtos {
    private BacktestDtos() {}
    public record RunIdResponse(String runId) {}
    public record Parameter(String name,int defaultValue,int minValue,int maxValue) {}
    public record Strategy(String code,String name,String version,String description,int minimumRequiredBars,List<Parameter> parameters) {}
    public record Page<T>(List<T> records,long total,int page,int pageSize) {}
    public record EquityPoint(int pointIndex,Instant openTime,BigDecimal equityRatio,BigDecimal drawdownRatio,boolean inPosition) {}
    public record Equity(boolean sampled,int totalPoints,List<EquityPoint> points) {}
    public record Trade(int tradeNo,int entrySignalIndex,int entryIndex,Instant entryTime,BigDecimal entryPrice,Integer exitSignalIndex,int exitIndex,Instant exitTime,BigDecimal exitPrice,BigDecimal amount,BigDecimal grossProfit,BigDecimal fee,BigDecimal netProfit,BigDecimal returnRatio,int barsHeld,boolean forcedExit,String exitReason) {}
    public record RunDetail(String runId,long datasetId,Instant datasetLastValidatedAt,String datasetLastSyncTaskId,String provider,String marketType,String dataType,String symbol,String intervalCode,Instant startOpenTimeInclusive,Instant endOpenTimeExclusive,String strategyCode,String strategyVersion,Map<String,Integer> requestedParameters,Map<String,Integer> resolvedParameters,BigDecimal orderAmount,BigDecimal feeRate,boolean forceCloseAtEnd,String status,BigDecimal progressPercent,String errorCode,String errorMessage,Instant queuedAt,Instant startedAt,Instant finishedAt,Integer barCount,Integer tradeCount,Integer winningTradeCount,Integer losingTradeCount,Integer breakEvenTradeCount,Map<String,Object> metrics,int equityPointCount) {}
}
