package com.aiprovider.mapper;

import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardTrainingCandidateRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WalkForwardFoldMapper {
  String COLUMNS =
      "Id,FoldId,StudyId,FoldIndex,TrainingStartOpenTimeMs,TrainingEndOpenTimeMs,ValidationStartOpenTimeMs,ValidationEndOpenTimeMs,ExperimentId,Status,ClaimToken,ClaimedAt,SelectedCandidateId,SelectedParametersJson,SelectedTrainingRunId,SelectedValidationRunId,SelectionMetricValue,ErrorCode,ErrorMessage,CreatedAt,UpdatedAt,StartedAt,FinishedAt";

  @Results(
      id = "walkForwardFoldRow",
      value = {
        @Result(column = "Id", property = "id"), @Result(column = "FoldId", property = "foldId"),
            @Result(column = "StudyId", property = "studyId"),
        @Result(column = "FoldIndex", property = "foldIndex"),
            @Result(column = "TrainingStartOpenTimeMs", property = "trainingStartOpenTimeMs"),
            @Result(column = "TrainingEndOpenTimeMs", property = "trainingEndOpenTimeMs"),
        @Result(column = "ValidationStartOpenTimeMs", property = "validationStartOpenTimeMs"),
            @Result(column = "ValidationEndOpenTimeMs", property = "validationEndOpenTimeMs"),
            @Result(column = "ExperimentId", property = "experimentId"),
        @Result(column = "Status", property = "status"),
            @Result(column = "ClaimToken", property = "claimToken"),
            @Result(column = "ClaimedAt", property = "claimedAt"),
        @Result(column = "SelectedCandidateId", property = "selectedCandidateId"),
            @Result(column = "SelectedParametersJson", property = "selectedParametersJson"),
            @Result(column = "SelectedTrainingRunId", property = "selectedTrainingRunId"),
        @Result(column = "SelectedValidationRunId", property = "selectedValidationRunId"),
            @Result(column = "SelectionMetricValue", property = "selectionMetricValue"),
            @Result(column = "ErrorCode", property = "errorCode"),
        @Result(column = "ErrorMessage", property = "errorMessage"),
            @Result(column = "CreatedAt", property = "createdAt"),
            @Result(column = "UpdatedAt", property = "updatedAt"),
        @Result(column = "StartedAt", property = "startedAt"),
            @Result(column = "FinishedAt", property = "finishedAt")
      })
  @Select("SELECT " + COLUMNS + " FROM q_walk_forward_fold WHERE FoldId=#{foldId}")
  WalkForwardFoldRow findByFoldId(@Param("foldId") String foldId);

  @ResultMap("walkForwardFoldRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_walk_forward_fold WHERE StudyId=#{studyId} ORDER BY FoldIndex ASC")
  List<WalkForwardFoldRow> findAllByStudyId(@Param("studyId") String studyId);

  @ResultMap("walkForwardFoldRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_walk_forward_fold WHERE StudyId=#{studyId} ORDER BY FoldIndex ASC LIMIT"
          + " #{limit} OFFSET #{offset}")
  List<WalkForwardFoldRow> findPage(
      @Param("studyId") String studyId, @Param("limit") int limit, @Param("offset") long offset);

  @Select("SELECT COUNT(*) FROM q_walk_forward_fold WHERE StudyId=#{studyId}")
  long count(@Param("studyId") String studyId);

  @Insert(
      "<script>INSERT INTO"
          + " q_walk_forward_fold(FoldId,StudyId,FoldIndex,TrainingStartOpenTimeMs,TrainingEndOpenTimeMs,ValidationStartOpenTimeMs,ValidationEndOpenTimeMs,ExperimentId,Status,CreatedAt,UpdatedAt)"
          + " VALUES <foreach collection='rows' item='r'"
          + " separator=','>(#{r.foldId},#{r.studyId},#{r.foldIndex},#{r.trainingStartOpenTimeMs},#{r.trainingEndOpenTimeMs},#{r.validationStartOpenTimeMs},#{r.validationEndOpenTimeMs},#{r.experimentId},'PENDING',#{r.createdAt},#{r.updatedAt})</foreach></script>")
  int insertBatch(@Param("rows") List<WalkForwardFoldRow> rows);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='CREATING_EXPERIMENT',ClaimToken=#{token},ClaimedAt=#{now},StartedAt=COALESCE(StartedAt,#{now}),UpdatedAt=#{now}"
          + " WHERE Id=(SELECT picked.Id FROM (SELECT Id FROM q_walk_forward_fold WHERE"
          + " StudyId=#{studyId} AND Status='PENDING' ORDER BY FoldIndex ASC LIMIT 1) picked) AND"
          + " Status='PENDING'")
  int claimNextPending(
      @Param("studyId") String studyId, @Param("token") String token, @Param("now") Instant now);

  @ResultMap("walkForwardFoldRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_walk_forward_fold WHERE StudyId=#{studyId} AND Status='CREATING_EXPERIMENT'"
          + " AND ClaimToken=#{token}")
  WalkForwardFoldRow findClaimed(@Param("studyId") String studyId, @Param("token") String token);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='PENDING',ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now} WHERE"
          + " FoldId=#{foldId} AND Status='CREATING_EXPERIMENT' AND ClaimToken=#{token}")
  int releaseCreationClaim(
      @Param("foldId") String foldId, @Param("token") String token, @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='PENDING',ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now} WHERE"
          + " Status='CREATING_EXPERIMENT' AND ClaimedAt < #{cutoff}")
  int resetStaleCreatingClaims(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='WAITING_EXPERIMENT',ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now} WHERE"
          + " FoldId=#{foldId} AND Status='CREATING_EXPERIMENT' AND ClaimToken=#{token}")
  int markWaitingExperiment(
      @Param("foldId") String foldId, @Param("token") String token, @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='COMPLETED',SelectedCandidateId=#{candidateId},SelectedParametersJson=#{parametersJson},SelectedTrainingRunId=#{trainingRunId},SelectedValidationRunId=#{validationRunId},SelectionMetricValue=#{metricValue},FinishedAt=#{now},UpdatedAt=#{now}"
          + " WHERE FoldId=#{foldId} AND Status='WAITING_EXPERIMENT'")
  int completeSelection(
      @Param("foldId") String foldId,
      @Param("candidateId") String candidateId,
      @Param("parametersJson") String parametersJson,
      @Param("trainingRunId") String trainingRunId,
      @Param("validationRunId") String validationRunId,
      @Param("metricValue") java.math.BigDecimal metricValue,
      @Param("now") Instant now);

  @Update(
      "UPDATE q_walk_forward_fold SET"
          + " Status='FAILED',ErrorCode=#{code},ErrorMessage=#{message},ClaimToken=NULL,ClaimedAt=NULL,FinishedAt=#{now},UpdatedAt=#{now}"
          + " WHERE FoldId=#{foldId} AND Status IN"
          + " ('PENDING','CREATING_EXPERIMENT','WAITING_EXPERIMENT')")
  int markFailed(
      @Param("foldId") String foldId,
      @Param("code") String code,
      @Param("message") String message,
      @Param("now") Instant now);

  @Results(
      id = "walkForwardTrainingCandidateRow",
      value = {
        @Result(column = "CandidateId", property = "candidateId"),
        @Result(column = "CandidateIndex", property = "candidateIndex"),
        @Result(column = "ParametersJson", property = "parametersJson"),
        @Result(column = "TrainingRunId", property = "trainingRunId"),
        @Result(column = "ValidationRunId", property = "validationRunId"),
        @Result(column = "TradeCount", property = "tradeCount"),
        @Result(column = "MetricValue", property = "metricValue")
      })
  @Select(
      "SELECT"
          + " c.CandidateId,c.CandidateIndex,c.ParametersJson,c.TrainingRunId,c.ValidationRunId,tr.TradeCount,${metricColumn}"
          + " AS MetricValue FROM q_backtest_experiment_candidate c JOIN q_backtest_run tr ON"
          + " tr.RunId=c.TrainingRunId WHERE c.ExperimentId=#{experimentId} AND"
          + " tr.Status='COMPLETED' AND tr.TradeCount >= #{minimumTrainTrades} AND ${metricColumn}"
          + " IS NOT NULL ORDER BY ${metricColumn} ${direction},c.CandidateIndex ASC LIMIT 1")
  WalkForwardTrainingCandidateRow findBestTrainingCandidate(
      @Param("experimentId") String experimentId,
      @Param("metricColumn") String metricColumn,
      @Param("direction") String direction,
      @Param("minimumTrainTrades") int minimumTrainTrades);
}
