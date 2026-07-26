package com.aiprovider.mapper;

import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WalkForwardStudyMapper {
  String COLUMNS =
      "Id,StudyId,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ParameterGridJson,WindowMode,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,StepBars,FoldCount,CandidateCountPerFold,TotalChildRuns,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,Status,ProgressPercent,ErrorCode,ErrorMessage,CreatedAt,UpdatedAt,StartedAt,FinishedAt";

  @Results(
      id = "walkForwardStudyRow",
      value = {
        @Result(column = "Id", property = "id"),
        @Result(column = "StudyId", property = "studyId"),
        @Result(column = "DatasetId", property = "datasetId"),
        @Result(column = "Provider", property = "provider"),
        @Result(column = "MarketType", property = "marketType"),
        @Result(column = "DataType", property = "dataType"),
        @Result(column = "Symbol", property = "symbol"),
        @Result(column = "IntervalCode", property = "intervalCode"),
        @Result(column = "StrategyCode", property = "strategyCode"),
        @Result(column = "StrategyVersion", property = "strategyVersion"),
        @Result(column = "ParameterGridJson", property = "parameterGridJson"),
        @Result(column = "WindowMode", property = "windowMode"),
        @Result(column = "StudyStartOpenTimeMs", property = "studyStartOpenTimeMs"),
        @Result(column = "StudyEndOpenTimeMs", property = "studyEndOpenTimeMs"),
        @Result(column = "TrainingBars", property = "trainingBars"),
        @Result(column = "ValidationBars", property = "validationBars"),
        @Result(column = "StepBars", property = "stepBars"),
        @Result(column = "FoldCount", property = "foldCount"),
        @Result(column = "CandidateCountPerFold", property = "candidateCountPerFold"),
        @Result(column = "TotalChildRuns", property = "totalChildRuns"),
        @Result(column = "SelectionMetric", property = "selectionMetric"),
        @Result(column = "MinimumTrainTrades", property = "minimumTrainTrades"),
        @Result(column = "OrderAmount", property = "orderAmount"),
        @Result(column = "FeeRate", property = "feeRate"),
        @Result(column = "ForceCloseAtEnd", property = "forceCloseAtEnd"),
        @Result(column = "Status", property = "status"),
        @Result(column = "ProgressPercent", property = "progressPercent"),
        @Result(column = "ErrorCode", property = "errorCode"),
        @Result(column = "ErrorMessage", property = "errorMessage"),
        @Result(column = "CreatedAt", property = "createdAt"),
        @Result(column = "UpdatedAt", property = "updatedAt"),
        @Result(column = "StartedAt", property = "startedAt"),
        @Result(column = "FinishedAt", property = "finishedAt")
      })
  @Select("SELECT " + COLUMNS + " FROM q_walk_forward_study WHERE StudyId=#{studyId}")
  WalkForwardStudyRow findByStudyId(@Param("studyId") String studyId);

  @ResultMap("walkForwardStudyRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_walk_forward_study WHERE Status IN ('QUEUED','RUNNING') ORDER BY CreatedAt"
          + " ASC,Id ASC")
  List<WalkForwardStudyRow> findNonTerminal();

  @ResultMap("walkForwardStudyRow")
  @Select(
      "<script>SELECT "
          + COLUMNS
          + " FROM q_walk_forward_study WHERE 1=1 <if test='status != null'>AND"
          + " Status=#{status}</if><if test='symbol != null'>AND Symbol=#{symbol}</if><if"
          + " test='strategyCode != null'>AND StrategyCode=#{strategyCode}</if> ORDER BY CreatedAt"
          + " DESC,Id DESC LIMIT #{limit} OFFSET #{offset}</script>")
  List<WalkForwardStudyRow> findPage(
      @Param("status") String status,
      @Param("symbol") String symbol,
      @Param("strategyCode") String strategyCode,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Select(
      "<script>SELECT COUNT(*) FROM q_walk_forward_study WHERE 1=1 <if test='status != null'>AND"
          + " Status=#{status}</if><if test='symbol != null'>AND Symbol=#{symbol}</if><if"
          + " test='strategyCode != null'>AND StrategyCode=#{strategyCode}</if></script>")
  long count(
      @Param("status") String status,
      @Param("symbol") String symbol,
      @Param("strategyCode") String strategyCode);

  @Insert(
      "INSERT INTO"
          + " q_walk_forward_study(StudyId,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ParameterGridJson,WindowMode,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,StepBars,FoldCount,CandidateCountPerFold,TotalChildRuns,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,Status,CreatedAt,UpdatedAt)"
          + " VALUES(#{studyId},#{datasetId},#{provider},#{marketType},#{dataType},#{symbol},#{intervalCode},#{strategyCode},#{strategyVersion},#{parameterGridJson},#{windowMode},#{studyStart},#{studyEnd},#{trainingBars},#{validationBars},#{stepBars},#{foldCount},#{candidateCountPerFold},#{totalChildRuns},#{selectionMetric},#{minimumTrainTrades},#{orderAmount},#{feeRate},#{forceCloseAtEnd},'QUEUED',#{createdAt},#{updatedAt})")
  int insert(WalkForwardStudyRow row);

  @Update(
      "UPDATE q_walk_forward_study SET"
          + " Status='RUNNING',StartedAt=COALESCE(StartedAt,#{now}),UpdatedAt=#{now} WHERE"
          + " StudyId=#{studyId} AND Status='QUEUED'")
  int markRunning(@Param("studyId") String studyId, @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_study SET"
          + " Status=#{status},ProgressPercent=#{progress},ErrorCode=#{errorCode},ErrorMessage=#{errorMessage},StartedAt=IF(#{status}='RUNNING',COALESCE(StartedAt,#{now}),StartedAt),FinishedAt=#{finishedAt},UpdatedAt=#{now}"
          + " WHERE StudyId=#{studyId} AND Status IN ('QUEUED','RUNNING')")
  int updateAggregate(
      @Param("studyId") String studyId,
      @Param("status") String status,
      @Param("progress") java.math.BigDecimal progress,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("finishedAt") Instant finishedAt,
      @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_study SET"
          + " Status='FAILED',ErrorCode=#{code},ErrorMessage=#{message},FinishedAt=#{now},UpdatedAt=#{now}"
          + " WHERE StudyId=#{studyId} AND Status IN ('QUEUED','RUNNING')")
  int fail(
      @Param("studyId") String studyId,
      @Param("code") String code,
      @Param("message") String message,
      @Param("now") Instant now);
}
