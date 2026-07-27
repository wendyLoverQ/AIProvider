package com.aiprovider.mapper;

import com.aiprovider.mapper.row.ResearchStudyRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ResearchStudyMapper {
  String COLUMNS = "r.Id,r.ResearchStudyId,r.Name,r.Description,r.DatasetId,r.Provider,r.MarketType,r.DataType,r.Symbol,r.IntervalCode,r.StrategyCode,r.StrategyVersion,r.ExecutionProfileCode,r.DirectionMode,r.OrderSizingMode,r.EvaluationMode,r.ParameterSpaceMode,r.ParameterSpaceJson,r.ExpandedParameterGridJson,r.CandidateCount,r.StudyStartOpenTimeMs,r.StudyEndOpenTimeMs,r.TrainingBars,r.ValidationBars,r.SelectionMetric,r.MinimumTrainTrades,r.OrderAmount,r.FeeRate,r.ForceCloseAtEnd,r.ComparisonGroupKey,r.WalkForwardStudyId,r.Status,r.ProgressPercent,r.ErrorCode,r.ErrorMessage,r.CreatedAt,r.StartedAt,r.FinishedAt,r.UpdatedAt";

  @Results(id = "researchStudyRow", value = {
      @Result(column="Id", property="id"), @Result(column="ResearchStudyId", property="researchStudyId"),
      @Result(column="Name", property="name"), @Result(column="Description", property="description"),
      @Result(column="DatasetId", property="datasetId"), @Result(column="Provider", property="provider"),
      @Result(column="MarketType", property="marketType"), @Result(column="DataType", property="dataType"),
      @Result(column="Symbol", property="symbol"), @Result(column="IntervalCode", property="intervalCode"),
      @Result(column="StrategyCode", property="strategyCode"), @Result(column="StrategyVersion", property="strategyVersion"),
      @Result(column="ExecutionProfileCode", property="executionProfileCode"), @Result(column="DirectionMode", property="directionMode"),
      @Result(column="OrderSizingMode", property="orderSizingMode"), @Result(column="EvaluationMode", property="evaluationMode"),
      @Result(column="ParameterSpaceMode", property="parameterSpaceMode"), @Result(column="ParameterSpaceJson", property="parameterSpaceJson"),
      @Result(column="ExpandedParameterGridJson", property="expandedParameterGridJson"), @Result(column="CandidateCount", property="candidateCount"),
      @Result(column="StudyStartOpenTimeMs", property="studyStartOpenTimeMs"), @Result(column="StudyEndOpenTimeMs", property="studyEndOpenTimeMs"),
      @Result(column="TrainingBars", property="trainingBars"), @Result(column="ValidationBars", property="validationBars"),
      @Result(column="SelectionMetric", property="selectionMetric"), @Result(column="MinimumTrainTrades", property="minimumTrainTrades"),
      @Result(column="OrderAmount", property="orderAmount"), @Result(column="FeeRate", property="feeRate"),
      @Result(column="ForceCloseAtEnd", property="forceCloseAtEnd"), @Result(column="ComparisonGroupKey", property="comparisonGroupKey"),
      @Result(column="WalkForwardStudyId", property="walkForwardStudyId"), @Result(column="Status", property="status"),
      @Result(column="ProgressPercent", property="progressPercent"), @Result(column="ErrorCode", property="errorCode"),
      @Result(column="ErrorMessage", property="errorMessage"), @Result(column="CreatedAt", property="createdAt"),
      @Result(column="StartedAt", property="startedAt"), @Result(column="FinishedAt", property="finishedAt"),
      @Result(column="UpdatedAt", property="updatedAt")})
  @Select("SELECT " + COLUMNS + " FROM q_research_study r LEFT JOIN q_walk_forward_study w ON w.StudyId=r.WalkForwardStudyId WHERE r.ResearchStudyId=#{researchStudyId}")
  ResearchStudyRow findByResearchStudyId(@Param("researchStudyId") String researchStudyId);

  @ResultMap("researchStudyRow")
  @Select("<script>SELECT " + COLUMNS + " FROM q_research_study r LEFT JOIN q_walk_forward_study w ON w.StudyId=r.WalkForwardStudyId WHERE r.ResearchStudyId IN <foreach collection='researchStudyIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
  List<ResearchStudyRow> findByResearchStudyIds(@Param("researchStudyIds") Collection<String> researchStudyIds);

  @ResultMap("researchStudyRow")
  @Select("<script>SELECT " + COLUMNS + " FROM q_research_study r LEFT JOIN q_walk_forward_study w ON w.StudyId=r.WalkForwardStudyId WHERE 1=1 <if test='status != null'>AND r.Status=#{status}</if><if test='datasetId != null'>AND r.DatasetId=#{datasetId}</if><if test='strategyCode != null'>AND r.StrategyCode=#{strategyCode}</if><if test='comparisonGroupKey != null'>AND r.ComparisonGroupKey=#{comparisonGroupKey}</if> ORDER BY r.CreatedAt DESC,r.ResearchStudyId ASC LIMIT #{limit} OFFSET #{offset}</script>")
  List<ResearchStudyRow> findPage(@Param("status") String status, @Param("datasetId") Long datasetId,
                                   @Param("strategyCode") String strategyCode, @Param("comparisonGroupKey") String comparisonGroupKey,
                                   @Param("limit") int limit, @Param("offset") long offset);

  @ResultMap("researchStudyRow")
  @Select("SELECT " + COLUMNS + " FROM q_research_study r LEFT JOIN q_walk_forward_study w ON w.StudyId=r.WalkForwardStudyId WHERE r.ComparisonGroupKey=#{comparisonGroupKey} ORDER BY r.CreatedAt DESC,r.ResearchStudyId ASC")
  List<ResearchStudyRow> findAllByComparisonGroupKey(@Param("comparisonGroupKey") String comparisonGroupKey);

  @Select("<script>SELECT COUNT(*) FROM q_research_study r LEFT JOIN q_walk_forward_study w ON w.StudyId=r.WalkForwardStudyId WHERE 1=1 <if test='status != null'>AND r.Status=#{status}</if><if test='datasetId != null'>AND r.DatasetId=#{datasetId}</if><if test='strategyCode != null'>AND r.StrategyCode=#{strategyCode}</if><if test='comparisonGroupKey != null'>AND r.ComparisonGroupKey=#{comparisonGroupKey}</if></script>")
  long count(@Param("status") String status, @Param("datasetId") Long datasetId,
             @Param("strategyCode") String strategyCode, @Param("comparisonGroupKey") String comparisonGroupKey);

  @ResultMap("researchStudyRow")
  @Select("SELECT " + COLUMNS + " FROM q_research_study r WHERE r.Status IN ('QUEUED','RUNNING') ORDER BY r.UpdatedAt ASC,r.ResearchStudyId ASC LIMIT #{limit}")
  List<ResearchStudyRow> findNonTerminal(@Param("limit") int limit);

  @Insert("INSERT INTO q_research_study(ResearchStudyId,Name,Description,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,EvaluationMode,ParameterSpaceMode,ParameterSpaceJson,ExpandedParameterGridJson,CandidateCount,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,ComparisonGroupKey,WalkForwardStudyId,Status,ProgressPercent,CreatedAt,UpdatedAt) VALUES(#{researchStudyId},#{name},#{description},#{datasetId},#{provider},#{marketType},#{dataType},#{symbol},#{intervalCode},#{strategyCode},#{strategyVersion},#{executionProfileCode},#{directionMode},#{orderSizingMode},#{evaluationMode},#{parameterSpaceMode},#{parameterSpaceJson},#{expandedParameterGridJson},#{candidateCount},#{studyStartOpenTimeMs},#{studyEndOpenTimeMs},#{trainingBars},#{validationBars},#{selectionMetric},#{minimumTrainTrades},#{orderAmount},#{feeRate},#{forceCloseAtEnd},#{comparisonGroupKey},#{walkForwardStudyId},'QUEUED',0,#{createdAt},#{updatedAt})")
  int insert(ResearchStudyRow row);

  @Update("UPDATE q_research_study SET Status=#{status},ProgressPercent=#{progressPercent},ErrorCode=#{errorCode},ErrorMessage=#{errorMessage},StartedAt=#{startedAt},FinishedAt=#{finishedAt},UpdatedAt=#{updatedAt} WHERE ResearchStudyId=#{researchStudyId} AND Status=#{expectedStatus} AND UpdatedAt=#{expectedUpdatedAt}")
  int updateAggregate(@Param("researchStudyId") String researchStudyId, @Param("expectedStatus") String expectedStatus,
                      @Param("expectedUpdatedAt") Instant expectedUpdatedAt, @Param("status") String status,
                      @Param("progressPercent") java.math.BigDecimal progressPercent, @Param("errorCode") String errorCode,
                      @Param("errorMessage") String errorMessage, @Param("startedAt") Instant startedAt,
                      @Param("finishedAt") Instant finishedAt, @Param("updatedAt") Instant updatedAt);
}
