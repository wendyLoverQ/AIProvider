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
    public record Metrics(Integer tradeCount,Integer winningTradeCount,Integer losingTradeCount,Integer breakEvenTradeCount,BigDecimal winRate,BigDecimal grossProfit,BigDecimal grossLoss,BigDecimal netProfit,BigDecimal totalReturnRatio,BigDecimal maximumDrawdownRatio,BigDecimal profitFactor,BigDecimal averageTradeReturnRatio,BigDecimal buyAndHoldReturnRatio,BigDecimal totalFees) {}
    public record EquityPoint(int pointIndex,Instant openTime,BigDecimal equityRatio,BigDecimal drawdownRatio,boolean inPosition) {}
    public record Equity(boolean sampled,int totalPoints,List<EquityPoint> points) {}
    public record Trade(int tradeNo,int entrySignalIndex,int entryIndex,Instant entryTime,BigDecimal entryPrice,Integer exitSignalIndex,int exitIndex,Instant exitTime,BigDecimal exitPrice,BigDecimal amount,BigDecimal grossProfit,BigDecimal fee,BigDecimal netProfit,BigDecimal returnRatio,int barsHeld,boolean forcedExit,String exitReason) {}
    public record RunDetail(String runId,long datasetId,Instant datasetLastValidatedAt,String datasetLastSyncTaskId,String provider,String marketType,String dataType,String symbol,String intervalCode,Instant startOpenTimeInclusive,Instant endOpenTimeExclusive,String strategyCode,String strategyVersion,Map<String,Integer> requestedParameters,Map<String,Integer> resolvedParameters,BigDecimal orderAmount,BigDecimal feeRate,boolean forceCloseAtEnd,String status,BigDecimal progressPercent,String errorCode,String errorMessage,Instant queuedAt,Instant startedAt,Instant finishedAt,Integer barCount,Integer tradeCount,Integer winningTradeCount,Integer losingTradeCount,Integer breakEvenTradeCount,String executionModel,List<String> warnings,Metrics metrics,int equityPointCount) {
        public RunDetail(String runId,long datasetId,Instant datasetLastValidatedAt,String datasetLastSyncTaskId,String provider,String marketType,String dataType,String symbol,String intervalCode,Instant startOpenTimeInclusive,Instant endOpenTimeExclusive,String strategyCode,String strategyVersion,Map<String,Integer> requestedParameters,Map<String,Integer> resolvedParameters,BigDecimal orderAmount,BigDecimal feeRate,boolean forceCloseAtEnd,String status,BigDecimal progressPercent,String errorCode,String errorMessage,Instant queuedAt,Instant startedAt,Instant finishedAt,Integer barCount,Integer tradeCount,Integer winningTradeCount,Integer losingTradeCount,Integer breakEvenTradeCount,Map<String,Object> oldMetrics,int equityPointCount) {
            this(runId,datasetId,datasetLastValidatedAt,datasetLastSyncTaskId,provider,marketType,dataType,symbol,intervalCode,startOpenTimeInclusive,endOpenTimeExclusive,strategyCode,strategyVersion,requestedParameters,resolvedParameters,orderAmount,feeRate,forceCloseAtEnd,status,progressPercent,errorCode,errorMessage,queuedAt,startedAt,finishedAt,barCount,tradeCount,winningTradeCount,losingTradeCount,breakEvenTradeCount,oldMetrics==null?null:String.valueOf(oldMetrics.get("_executionModel")),warnings(oldMetrics),toMetrics(oldMetrics),equityPointCount);
        }
        private static Metrics toMetrics(Map<String,Object> m){if(m==null)return new Metrics(null,null,null,null,null,null,null,null,null,null,null,null,null,null);return new Metrics(null,null,null,null,decimal(m,"winRate"),decimal(m,"grossProfit"),decimal(m,"grossLoss"),decimal(m,"netProfit"),decimal(m,"totalReturnRatio"),decimal(m,"maximumDrawdownRatio"),decimal(m,"profitFactor"),decimal(m,"averageTradeReturnRatio"),decimal(m,"buyAndHoldReturnRatio"),decimal(m,"totalFees"));}
        private static BigDecimal decimal(Map<String,Object> m,String key){Object value=m.get(key);return value instanceof BigDecimal b?b:value==null?null:new BigDecimal(String.valueOf(value));}
        private static List<String> warnings(Map<String,Object> m){Object value=m==null?null:m.get("_warnings");return value instanceof List<?> list?list.stream().map(String::valueOf).toList():List.of();}
    }
}
