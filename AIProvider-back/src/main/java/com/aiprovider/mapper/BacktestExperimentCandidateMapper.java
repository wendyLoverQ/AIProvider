package com.aiprovider.mapper;

import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BacktestExperimentCandidateMapper {
  String COLUMNS =
      "Id,CandidateId,ExperimentId,CandidateIndex,ParametersJson,TrainingRunId,ValidationRunId,DispatchStatus,ClaimToken,ClaimedAt,ErrorCode,ErrorMessage,CreatedAt,UpdatedAt";

  @Results(
      id = "backtestExperimentCandidateRow",
      value = {
        @Result(column = "Id", property = "id"),
        @Result(column = "CandidateId", property = "candidateId"),
        @Result(column = "ExperimentId", property = "experimentId"),
        @Result(column = "CandidateIndex", property = "candidateIndex"),
        @Result(column = "ParametersJson", property = "parametersJson"),
        @Result(column = "TrainingRunId", property = "trainingRunId"),
        @Result(column = "ValidationRunId", property = "validationRunId"),
        @Result(column = "DispatchStatus", property = "dispatchStatus"),
        @Result(column = "ClaimToken", property = "claimToken"),
        @Result(column = "ClaimedAt", property = "claimedAt"),
        @Result(column = "ErrorCode", property = "errorCode"),
        @Result(column = "ErrorMessage", property = "errorMessage"),
        @Result(column = "CreatedAt", property = "createdAt"),
        @Result(column = "UpdatedAt", property = "updatedAt")
      })
  @Select("SELECT " + COLUMNS + " FROM q_backtest_experiment_candidate WHERE CandidateId=#{id}")
  BacktestExperimentCandidateRow findByCandidateId(@Param("id") String id);

  @ResultMap("backtestExperimentCandidateRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_backtest_experiment_candidate WHERE ExperimentId=#{experimentId} ORDER BY"
          + " CandidateIndex ASC LIMIT #{limit} OFFSET #{offset}")
  List<BacktestExperimentCandidateRow> findPage(
      @Param("experimentId") String experimentId,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @ResultMap("backtestExperimentCandidateRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_backtest_experiment_candidate WHERE ExperimentId=#{experimentId} ORDER BY"
          + " CandidateIndex ASC")
  List<BacktestExperimentCandidateRow> findAll(@Param("experimentId") String experimentId);

  @ResultMap("backtestExperimentCandidateRow")
  @Select(
      "<script>SELECT "
          + COLUMNS
          + " FROM q_backtest_experiment_candidate WHERE ExperimentId IN <foreach"
          + " collection='experimentIds' item='experimentId' open='(' separator=','"
          + " close=')'>#{experimentId}</foreach> ORDER BY ExperimentId ASC, CandidateIndex"
          + " ASC</script>")
  List<BacktestExperimentCandidateRow> findAllByExperimentIds(
      @Param("experimentIds") List<String> experimentIds);

  @ResultMap("backtestExperimentCandidateRow")
  @Select(
      "SELECT"
          + " c.Id,c.CandidateId,c.ExperimentId,c.CandidateIndex,c.ParametersJson,c.TrainingRunId,c.ValidationRunId,c.DispatchStatus,c.ClaimToken,c.ClaimedAt,c.ErrorCode,c.ErrorMessage,c.CreatedAt,c.UpdatedAt"
          + " FROM q_backtest_experiment_candidate c LEFT JOIN q_backtest_run tr ON"
          + " tr.RunId=c.TrainingRunId LEFT JOIN q_backtest_run vr ON vr.RunId=c.ValidationRunId"
          + " WHERE c.ExperimentId=#{experimentId} ORDER BY CASE WHEN ${sortExpression} IS NULL"
          + " THEN 1 ELSE 0 END, ${sortExpression} ${sortOrder}, c.CandidateIndex ASC LIMIT"
          + " #{limit} OFFSET #{offset}")
  List<BacktestExperimentCandidateRow> findPageSorted(
      @Param("experimentId") String experimentId,
      @Param("limit") int limit,
      @Param("offset") long offset,
      @Param("sortExpression") String sortExpression,
      @Param("sortOrder") String sortOrder);

  @Select("SELECT COUNT(*) FROM q_backtest_experiment_candidate WHERE ExperimentId=#{experimentId}")
  long count(@Param("experimentId") String experimentId);

  @Insert(
      "<script>INSERT INTO"
          + " q_backtest_experiment_candidate(CandidateId,ExperimentId,CandidateIndex,ParametersJson,TrainingRunId,ValidationRunId,DispatchStatus,CreatedAt,UpdatedAt)"
          + " VALUES <foreach collection='rows' item='r'"
          + " separator=','>(#{r.candidateId},#{r.experimentId},#{r.candidateIndex},#{r.parametersJson},#{r.trainingRunId},#{r.validationRunId},'PENDING',#{r.createdAt},#{r.updatedAt})</foreach></script>")
  int insertBatch(@Param("rows") List<BacktestExperimentCandidateRow> rows);

  @Update(
      "UPDATE q_backtest_experiment_candidate SET"
          + " DispatchStatus='CLAIMED',ClaimToken=#{token},ClaimedAt=#{now},UpdatedAt=#{now} WHERE"
          + " CandidateId=(SELECT CandidateId FROM (SELECT CandidateId FROM"
          + " q_backtest_experiment_candidate WHERE ExperimentId=#{experimentId} AND"
          + " DispatchStatus='PENDING' ORDER BY CandidateIndex ASC LIMIT 1) picked) AND"
          + " DispatchStatus='PENDING'")
  int claimNextPending(
      @Param("experimentId") String experimentId,
      @Param("token") String token,
      @Param("now") Instant now);

  @ResultMap("backtestExperimentCandidateRow")
  @Select(
      "SELECT "
          + COLUMNS
          + " FROM q_backtest_experiment_candidate WHERE ExperimentId=#{experimentId} AND"
          + " ClaimToken=#{token}")
  BacktestExperimentCandidateRow findClaimed(
      @Param("experimentId") String experimentId, @Param("token") String token);

  @Update(
      "UPDATE q_backtest_experiment_candidate SET"
          + " DispatchStatus='DISPATCHED',ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now} WHERE"
          + " CandidateId=#{candidateId} AND DispatchStatus='CLAIMED' AND ClaimToken=#{token}")
  int markDispatched(
      @Param("candidateId") String candidateId,
      @Param("token") String token,
      @Param("now") Instant now);

  @Update(
      "UPDATE q_backtest_experiment_candidate SET"
          + " DispatchStatus='FAILED',ErrorCode=#{code},ErrorMessage=#{message},ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now}"
          + " WHERE CandidateId=#{candidateId} AND DispatchStatus='CLAIMED' AND"
          + " ClaimToken=#{token}")
  int markDispatchFailed(
      @Param("candidateId") String candidateId,
      @Param("token") String token,
      @Param("code") String code,
      @Param("message") String message,
      @Param("now") Instant now);

  @Update(
      "UPDATE q_backtest_experiment_candidate SET"
          + " DispatchStatus='PENDING',ClaimToken=NULL,ClaimedAt=NULL,UpdatedAt=#{now} WHERE"
          + " DispatchStatus='CLAIMED' AND ClaimedAt < #{cutoff}")
  int resetStaleClaims(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
