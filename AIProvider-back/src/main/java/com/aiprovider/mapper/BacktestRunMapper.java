package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Mapper
public interface BacktestRunMapper {
    @Insert("INSERT INTO q_backtest_run(RunId,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StartOpenTimeMs,EndOpenTimeExclusiveMs,StrategyCode,StrategyVersion,RequestedParametersJson,OrderAmount,FeeRate,ForceCloseAtEnd,Status,ProgressPercent,QueuedAt,UpdatedAt) VALUES(#{runId},#{datasetId},#{provider},#{marketType},#{dataType},#{symbol},#{intervalCode},#{startMs},#{endMs},#{strategyCode},#{strategyVersion},#{requestedJson},#{orderAmount},#{feeRate},#{forceCloseAtEnd},'QUEUED',0,#{now},#{now})") int insert(Map<String,Object> row);
    @Select("SELECT * FROM q_backtest_run WHERE RunId=#{runId}") Map<String,Object> findByRunId(@Param("runId") String runId);
    @Select("<script>SELECT * FROM q_backtest_run WHERE 1=1 <if test='status != null'>AND Status=#{status}</if><if test='symbol != null'>AND Symbol=#{symbol}</if><if test='strategyCode != null'>AND StrategyCode=#{strategyCode}</if> ORDER BY QueuedAt DESC, Id DESC LIMIT #{limit} OFFSET #{offset}</script>") List<Map<String,Object>> findPage(@Param("status") String status,@Param("symbol") String symbol,@Param("strategyCode") String strategyCode,@Param("limit") int limit,@Param("offset") int offset);
    @Select("<script>SELECT COUNT(*) FROM q_backtest_run WHERE 1=1 <if test='status != null'>AND Status=#{status}</if><if test='symbol != null'>AND Symbol=#{symbol}</if><if test='strategyCode != null'>AND StrategyCode=#{strategyCode}</if></script>") long count(@Param("status") String status,@Param("symbol") String symbol,@Param("strategyCode") String strategyCode);
    @Select("SELECT * FROM q_backtest_run WHERE Status IN ('QUEUED','LOADING_SNAPSHOT','RUNNING_ENGINE','PERSISTING') ORDER BY QueuedAt ASC, Id ASC") List<Map<String,Object>> findNonTerminal();
    @Update("UPDATE q_backtest_run SET Status=#{toStatus},ProgressPercent=#{progress},UpdatedAt=#{now},StartedAt=IF(#{toStatus}='LOADING_SNAPSHOT',#{now},StartedAt) WHERE RunId=#{runId} AND Status=#{fromStatus}") int transition(@Param("runId") String runId,@Param("fromStatus") String fromStatus,@Param("toStatus") String toStatus,@Param("progress") BigDecimal progress,@Param("now") Instant now);
    @Update("UPDATE q_backtest_run SET DatasetLastValidatedAt=#{validatedAt},DatasetLastSyncTaskId=#{syncTaskId},Provider=#{provider},MarketType=#{marketType},DataType=#{dataType},Symbol=#{symbol},IntervalCode=#{intervalCode},BarCount=#{barCount},UpdatedAt=#{now} WHERE RunId=#{runId}") int updateSnapshot(Map<String,Object> row);
    @Update("UPDATE q_backtest_run SET ResolvedParametersJson=#{resolvedJson},BarCount=#{barCount},TradeCount=#{tradeCount},WinningTradeCount=#{winningTradeCount},LosingTradeCount=#{losingTradeCount},BreakEvenTradeCount=#{breakEvenTradeCount},WinRate=#{winRate},GrossProfit=#{grossProfit},GrossLoss=#{grossLoss},NetProfit=#{netProfit},TotalReturnRatio=#{totalReturnRatio},MaximumDrawdownRatio=#{maximumDrawdownRatio},ProfitFactor=#{profitFactor},AverageTradeReturnRatio=#{averageTradeReturnRatio},BuyAndHoldReturnRatio=#{buyAndHoldReturnRatio},TotalFees=#{totalFees},ExecutionModel=#{executionModel},WarningsJson=#{warningsJson},EquityPointCount=#{equityPointCount},Status='COMPLETED',ProgressPercent=100,FinishedAt=#{now},UpdatedAt=#{now} WHERE RunId=#{runId} AND Status='PERSISTING'") int complete(Map<String,Object> row);
    @Update("UPDATE q_backtest_run SET Status='FAILED',ErrorCode=#{errorCode},ErrorMessage=#{errorMessage},FinishedAt=#{now},UpdatedAt=#{now} WHERE RunId=#{runId} AND Status NOT IN ('COMPLETED','FAILED')") int fail(@Param("runId") String runId,@Param("errorCode") String code,@Param("errorMessage") String message,@Param("now") Instant now);
}
