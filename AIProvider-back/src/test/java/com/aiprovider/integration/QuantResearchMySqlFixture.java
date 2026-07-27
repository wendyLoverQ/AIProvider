package com.aiprovider.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class QuantResearchMySqlFixture {
  private final NamedParameterJdbcTemplate jdbc;
  QuantResearchMySqlFixture(JdbcTemplate jdbc) { this.jdbc = new NamedParameterJdbcTemplate(jdbc); }

  void insertTerminalWalkForwardStudy(String id, int foldCount, String status) {
    String sql = "INSERT INTO q_walk_forward_study(StudyId,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,ParameterGridJson,WindowMode,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,StepBars,FoldCount,CandidateCountPerFold,TotalChildRuns,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,Status,ProgressPercent,ErrorCode,ErrorMessage,CreatedAt,UpdatedAt,StartedAt,FinishedAt,SuccessfulOosFolds,FailedFolds,HasOosGaps,OosTotalReturnRatio,OosMaximumDrawdownRatio,OosTradeCount,OosTotalFees,ParameterChanges,OosAggregateVersion) VALUES (:id,1,'BINANCE_USDM','USDM_PERPETUAL','CANDLE','BTCUSDT','1m','EMA_CROSS_LONG_ONLY','1.0.0','PROFILE','LONG_ONLY','BASE_QUANTITY','{\"fastPeriod\":[5,7]}','ROLLING',0,10000,1,1,1,:foldCount,1,2,'TRAIN_TOTAL_RETURN_RATIO',0,1,0.001,1,:status,100,NULL,NULL,:time,:time,:time,:time,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)";
    MapSqlParameterSource source = params().addValue("id", id).addValue("foldCount", foldCount).addValue("status", status).addValue("time", Instant.EPOCH);
    jdbc.update(sql, source);
  }

  void insertTerminalFold(String studyId, int index, String status, String validationRunId, String parameters) {
    String id = UUID.randomUUID().toString();
    String sql = "INSERT INTO q_walk_forward_fold(FoldId,StudyId,FoldIndex,TrainingStartOpenTimeMs,TrainingEndOpenTimeMs,ValidationStartOpenTimeMs,ValidationEndOpenTimeMs,ExperimentId,Status,SelectedCandidateId,SelectedParametersJson,SelectedTrainingRunId,SelectedValidationRunId,SelectionMetricValue,ErrorCode,ErrorMessage,CreatedAt,UpdatedAt,StartedAt,FinishedAt) VALUES (:foldId,:studyId,:foldIndex,:start,:trainEnd,:validationStart,:validationEnd,:experimentId,:status,:candidate,:parameters,:trainingRun,:validationRun,:metric,:errorCode,:errorMessage,:time,:time,:time,:time)";
    MapSqlParameterSource source = params().addValue("foldId", id).addValue("studyId", studyId).addValue("foldIndex", index).addValue("start", index * 1000L)
        .addValue("trainEnd", index * 1000L + 500).addValue("validationStart", index * 1000L + 500).addValue("validationEnd", index * 1000L + 900)
        .addValue("experimentId", UUID.randomUUID().toString()).addValue("status", status)
        .addValue("candidate", "FAILED".equals(status) ? null : "candidate-" + index)
        .addValue("parameters", "FAILED".equals(status) ? null : parameters)
        .addValue("trainingRun", "FAILED".equals(status) ? null : "t" + index)
        .addValue("validationRun", "FAILED".equals(status) ? null : validationRunId)
        .addValue("metric", "FAILED".equals(status) ? null : BigDecimal.ZERO)
        .addValue("errorCode", "FAILED".equals(status) ? "TEST_FAILED" : null)
        .addValue("errorMessage", "FAILED".equals(status) ? "fixture failed" : null).addValue("time", Instant.EPOCH);
    jdbc.update(sql, source);
  }

  void insertCompletedBacktestRun(String runId, int trades, String totalReturn, String fees) {
    String sql = "INSERT INTO q_backtest_run(RunId,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StartOpenTimeMs,EndOpenTimeExclusiveMs,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,RequestedParametersJson,ResolvedParametersJson,OrderAmount,FeeRate,ForceCloseAtEnd,Status,ProgressPercent,TradeCount,TotalReturnRatio,MaximumDrawdownRatio,TotalFees,ExecutionModel,EquityPointCount,QueuedAt,StartedAt,FinishedAt,UpdatedAt) VALUES (:runId,1,'BINANCE_USDM','USDM_PERPETUAL','CANDLE','BTCUSDT','1m',0,10000,'EMA_CROSS_LONG_ONLY','1.0.0','PROFILE','LONG_ONLY','BASE_QUANTITY','{}','{}',1,0.001,1,'COMPLETED',100,:trades,:totalReturn,0.1,:fees,'BACKTEST',0,:time,:time,:time,:time)";
    MapSqlParameterSource source = params().addValue("runId", runId).addValue("trades", trades).addValue("totalReturn", new BigDecimal(totalReturn)).addValue("fees", new BigDecimal(fees)).addValue("time", Instant.EPOCH);
    jdbc.update(sql, source);
  }

  void insertBacktestEquity(String runId, List<String> values, long timeOffset) {
    for (int index = 0; index < values.size(); index++) {
      String sql = "INSERT INTO q_backtest_equity(RunId,PointIndex,OpenTimeMs,EquityRatio,DrawdownRatio,InPosition) VALUES (:runId,:pointIndex,:time,:equity,0,0)";
      MapSqlParameterSource source = params().addValue("runId", runId).addValue("pointIndex", index).addValue("time", timeOffset + index).addValue("equity", new BigDecimal(values.get(index)));
      jdbc.update(sql, source);
    }
  }

  void insertResearchStudySnapshot(String id, String childId, String status, String group) {
    insertResearchStudySnapshot(id, childId, status, group, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, null, null);
  }

  void insertResearchStudySnapshot(String id, String childId, String status, String group,
      Instant startedAt, Instant finishedAt, Instant updatedAt, String errorCode, String errorMessage) {
    String sql = "INSERT INTO q_research_study(ResearchStudyId,Name,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,EvaluationMode,ParameterSpaceMode,ParameterSpaceJson,ExpandedParameterGridJson,CandidateCount,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,ComparisonGroupKey,WalkForwardStudyId,Status,ProgressPercent,CreatedAt,UpdatedAt,StartedAt,FinishedAt,ErrorCode,ErrorMessage) VALUES (:id,'research',1,'BINANCE_USDM','USDM_PERPETUAL','CANDLE','BTCUSDT','1m','EMA_CROSS_LONG_ONLY','1.0.0','PROFILE','LONG_ONLY','BASE_QUANTITY','WALK_FORWARD','STRATEGY_DEFAULT','{\"fastPeriod\":{\"min\":5,\"max\":7,\"step\":2}}','{\"fastPeriod\":[5,7]}',2,0,10000,1,1,'TRAIN_TOTAL_RETURN_RATIO',0,1,0.001,1,:group,:child,:status,100,:time,:time,:time,:time,NULL,NULL)";
    MapSqlParameterSource source = params().addValue("id", id).addValue("child", childId).addValue("status", status).addValue("group", group)
        .addValue("time", updatedAt).addValue("startedAt", startedAt).addValue("finishedAt", finishedAt)
        .addValue("errorCode", errorCode).addValue("errorMessage", errorMessage);
    sql = sql.replace(":time,:time,:time,:time,NULL,NULL", ":time,:time,:startedAt,:finishedAt,:errorCode,:errorMessage");
    jdbc.update(sql, source);
  }

  private MapSqlParameterSource params() { return new MapSqlParameterSource(); }
}
