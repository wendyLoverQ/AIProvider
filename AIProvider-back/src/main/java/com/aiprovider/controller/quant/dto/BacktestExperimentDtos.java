package com.aiprovider.controller.quant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BacktestExperimentDtos {
    private BacktestExperimentDtos() {}
    public record CreateResponse(String experimentId,int candidateCount,int totalLegs) {}
    public record ExperimentSummary(String experimentId,long datasetId,String provider,String marketType,String dataType,String symbol,String intervalCode,String strategyCode,String strategyVersion,String executionProfileCode,String directionMode,String orderSizingMode,Map<String,List<Integer>> parameterGrid,int candidateCount,Instant trainingStartOpenTimeInclusive,Instant trainingEndOpenTimeExclusive,Instant validationStartOpenTimeInclusive,Instant validationEndOpenTimeExclusive,BigDecimal initialCapital,BigDecimal orderAmount,BigDecimal feeRate,boolean forceCloseAtEnd,String status,BigDecimal progressPercent,int pendingCandidates,int activeCandidates,int completedCandidates,int failedCandidates,int completedLegs,int failedLegs,String errorCode,String errorMessage,Instant createdAt,Instant startedAt,Instant finishedAt,Instant updatedAt) {}
    public record SegmentResult(String segmentType,String runId,String status,BigDecimal progressPercent,String errorCode,String errorMessage,Integer barCount,Integer tradeCount,BacktestDtos.Metrics metrics,Instant startedAt,Instant finishedAt) {}
    public record CandidateResult(String candidateId,int candidateIndex,Map<String,Integer> parameters,String dispatchStatus,SegmentResult training,SegmentResult validation) {}
}
