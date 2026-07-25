package com.aiprovider.mapper;

import com.aiprovider.config.quant.InstantEpochMillisTypeHandler;
import com.aiprovider.config.quant.KlineIntervalTypeHandler;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * q_market_sync_task 表 MyBatis Mapper。
 *
 * RequestedStartTimeMs、RequestedEndTimeMs、NormalizedStartTimeMs、NormalizedEndTimeMs
 * 以 epoch 毫秒 BIGINT 存储，使用 {@link InstantEpochMillisTypeHandler}。
 * IntervalCode 使用 {@link KlineIntervalTypeHandler}。
 * QueuedAt、StartedAt、FinishedAt、UpdatedAt 为 DATETIME(6)，使用 MyBatis 内置 InstantTypeHandler。
 */
@Mapper
public interface MarketSyncTaskMapper {

    String COLUMNS = "Id, TaskId, DatasetId, ActiveDatasetKey, Provider, MarketType, DataType, " +
            "Symbol, IntervalCode, RequestedStartTimeMs, RequestedEndTimeMs, " +
            "NormalizedStartTimeMs, NormalizedEndTimeMs, ExpectedCount, FetchedCount, " +
            "InsertedCount, ExistingCount, ConflictCount, GapCount, GapSegmentCount, " +
            "BatchCount, ProgressPercent, Status, SourceMode, CurrentSourceFile, " +
            "PlannedFileCount, CompletedFileCount, ErrorCode, ErrorMessage, UsedWeight1m, " +
            "RetryAfterSeconds, QueuedAt, StartedAt, FinishedAt, UpdatedAt";

    @Results(id = "taskResult", value = {
            @Result(column = "Id", property = "id"),
            @Result(column = "TaskId", property = "taskId"),
            @Result(column = "DatasetId", property = "datasetId"),
            @Result(column = "ActiveDatasetKey", property = "activeDatasetKey"),
            @Result(column = "Provider", property = "provider"),
            @Result(column = "MarketType", property = "marketType"),
            @Result(column = "DataType", property = "dataType"),
            @Result(column = "Symbol", property = "symbol"),
            @Result(column = "IntervalCode", property = "interval", typeHandler = KlineIntervalTypeHandler.class),
            @Result(column = "RequestedStartTimeMs", property = "requestedStartTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "RequestedEndTimeMs", property = "requestedEndTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "NormalizedStartTimeMs", property = "normalizedStartTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "NormalizedEndTimeMs", property = "normalizedEndTime", typeHandler = InstantEpochMillisTypeHandler.class),
            @Result(column = "ExpectedCount", property = "expectedCount"),
            @Result(column = "FetchedCount", property = "fetchedCount"),
            @Result(column = "InsertedCount", property = "insertedCount"),
            @Result(column = "ExistingCount", property = "existingCount"),
            @Result(column = "ConflictCount", property = "conflictCount"),
            @Result(column = "GapCount", property = "gapCount"),
            @Result(column = "GapSegmentCount", property = "gapSegmentCount"),
            @Result(column = "BatchCount", property = "batchCount"),
            @Result(column = "ProgressPercent", property = "progressPercent"),
            @Result(column = "Status", property = "status"),
            @Result(column = "SourceMode", property = "sourceMode"),
            @Result(column = "CurrentSourceFile", property = "currentSourceFile"),
            @Result(column = "PlannedFileCount", property = "plannedFileCount"),
            @Result(column = "CompletedFileCount", property = "completedFileCount"),
            @Result(column = "ErrorCode", property = "errorCode"),
            @Result(column = "ErrorMessage", property = "errorMessage"),
            @Result(column = "UsedWeight1m", property = "usedWeight1m"),
            @Result(column = "RetryAfterSeconds", property = "retryAfterSeconds"),
            @Result(column = "QueuedAt", property = "queuedAt"),
            @Result(column = "StartedAt", property = "startedAt"),
            @Result(column = "FinishedAt", property = "finishedAt"),
            @Result(column = "UpdatedAt", property = "updatedAt")
    })
    @Select("SELECT " + COLUMNS + " FROM q_market_sync_task WHERE TaskId=#{taskId}")
    MarketSyncTask findByTaskId(@Param("taskId") String taskId);

    @ResultMap("taskResult")
    @Select("SELECT " + COLUMNS + " FROM q_market_sync_task " +
            "ORDER BY QueuedAt DESC, Id DESC LIMIT #{limit} OFFSET #{offset}")
    List<MarketSyncTask> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @ResultMap("taskResult")
    @Select("SELECT " + COLUMNS + " FROM q_market_sync_task WHERE Status IN ('QUEUED','DOWNLOADING','WRITING','VALIDATING') " +
            "ORDER BY QueuedAt ASC")
    List<MarketSyncTask> findAllNonTerminal();

    @Insert("INSERT INTO q_market_sync_task(TaskId,DatasetId,ActiveDatasetKey,Provider,MarketType,DataType," +
            "Symbol,IntervalCode,RequestedStartTimeMs,RequestedEndTimeMs,NormalizedStartTimeMs,NormalizedEndTimeMs," +
            "ExpectedCount,Status,SourceMode) VALUES(" +
            "#{task.taskId},#{task.datasetId},#{task.activeDatasetKey},#{task.provider},#{task.marketType}," +
            "#{task.dataType},#{task.symbol},#{task.interval.code}," +
            "#{task.requestedStartTime.toEpochMilli},#{task.requestedEndTime.toEpochMilli}," +
            "#{task.normalizedStartTime.toEpochMilli},#{task.normalizedEndTime.toEpochMilli}," +
            "#{task.expectedCount},#{task.status},#{task.sourceMode})")
    @Options(useGeneratedKeys = true, keyProperty = "task.id", keyColumn = "Id")
    int insert(@Param("task") MarketSyncTask task);

    @Update("UPDATE q_market_sync_task SET Status=#{status}," +
            "FetchedCount=#{fetchedCount},InsertedCount=#{insertedCount}," +
            "ExistingCount=#{existingCount},ConflictCount=#{conflictCount}," +
            "GapCount=#{gapCount},BatchCount=#{batchCount}," +
            "ProgressPercent=#{progressPercent}," +
            "UsedWeight1m=#{usedWeight1m}," +
            "StartedAt=COALESCE(StartedAt,CURRENT_TIMESTAMP(6)) " +
            "WHERE TaskId=#{taskId}")
    int updateProgress(@Param("taskId") String taskId, @Param("status") String status,
                       @Param("fetchedCount") long fetchedCount, @Param("insertedCount") long insertedCount,
                       @Param("existingCount") long existingCount, @Param("conflictCount") long conflictCount,
                       @Param("gapCount") long gapCount, @Param("batchCount") int batchCount,
                       @Param("progressPercent") BigDecimal progressPercent,
                       @Param("usedWeight1m") Integer usedWeight1m);

    @Update("UPDATE q_market_sync_task SET Status='FAILED',ErrorCode=#{errorCode}," +
            "ErrorMessage=#{errorMessage},UsedWeight1m=#{usedWeight1m}," +
            "RetryAfterSeconds=#{retryAfterSeconds},ActiveDatasetKey=NULL," +
            "FinishedAt=CURRENT_TIMESTAMP(6) WHERE TaskId=#{taskId}")
    int markFailed(@Param("taskId") String taskId, @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage,
                   @Param("usedWeight1m") Integer usedWeight1m,
                   @Param("retryAfterSeconds") Integer retryAfterSeconds);

    @Update("UPDATE q_market_sync_task SET Status='COMPLETED'," +
            "FetchedCount=#{fetchedCount},InsertedCount=#{insertedCount}," +
            "ExistingCount=#{existingCount},GapCount=#{gapCount}," +
            "GapSegmentCount=#{gapSegmentCount}," +
            "ProgressPercent=100.0000,ActiveDatasetKey=NULL," +
            "FinishedAt=CURRENT_TIMESTAMP(6) WHERE TaskId=#{taskId}")
    int markCompleted(@Param("taskId") String taskId, @Param("fetchedCount") long fetchedCount,
                      @Param("insertedCount") long insertedCount, @Param("existingCount") long existingCount,
                      @Param("gapCount") long gapCount, @Param("gapSegmentCount") int gapSegmentCount);

    @Update("UPDATE q_market_sync_task SET Status=#{status}," +
            "CurrentSourceFile=#{currentSourceFile}," +
            "PlannedFileCount=COALESCE(#{plannedFileCount},PlannedFileCount)," +
            "CompletedFileCount=GREATEST(CompletedFileCount,#{completedFileCount})," +
            "FetchedCount=#{fetchedCount},InsertedCount=#{insertedCount}," +
            "ExistingCount=#{existingCount},ConflictCount=#{conflictCount}," +
            "BatchCount=#{batchCount},ProgressPercent=#{progressPercent}," +
            "StartedAt=COALESCE(StartedAt,CURRENT_TIMESTAMP(6)) " +
            "WHERE TaskId=#{taskId}")
    int updateArchiveProgress(@Param("taskId") String taskId, @Param("status") String status,
                               @Param("sourceMode") String sourceMode,
                               @Param("currentSourceFile") String currentSourceFile,
                               @Param("plannedFileCount") Integer plannedFileCount,
                               @Param("completedFileCount") int completedFileCount,
                               @Param("fetchedCount") long fetchedCount,
                               @Param("insertedCount") long insertedCount,
                               @Param("existingCount") long existingCount,
                               @Param("conflictCount") long conflictCount,
                               @Param("batchCount") int batchCount,
                               @Param("progressPercent") BigDecimal progressPercent);

    @Update("UPDATE q_market_sync_task SET ActiveDatasetKey=NULL WHERE TaskId=#{taskId}")
    int clearActiveLock(@Param("taskId") String taskId);

    @Update("UPDATE q_market_sync_task SET Status='FAILED',ErrorCode='SERVICE_RESTART_INTERRUPTED'," +
            "ErrorMessage='服务重启时任务非终态，已标记中断'," +
            "ActiveDatasetKey=NULL,FinishedAt=CURRENT_TIMESTAMP(6) " +
            "WHERE Status IN ('QUEUED','DOWNLOADING','WRITING','VALIDATING')")
    int markNonTerminalAsInterrupted();
}
