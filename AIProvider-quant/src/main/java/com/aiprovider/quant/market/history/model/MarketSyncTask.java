package com.aiprovider.quant.market.history.model;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 历史行情同步任务。
 *
 * 对应 {@code q_market_sync_task} 表。记录每次同步的请求范围、归一化范围、
 * 进度、计数和状态。任务状态真实来自数据库，不在前端伪造。
 */
public class MarketSyncTask {

    private long id;
    private String taskId;
    private long datasetId;
    private String activeDatasetKey;
    private MarketProviderId provider;
    private MarketType marketType;
    private MarketDataType dataType;
    private String symbol;
    private KlineInterval interval;
    private Instant requestedStartTime;
    private Instant requestedEndTime;
    private Instant normalizedStartTime;
    private Instant normalizedEndTime;
    private long expectedCount;
    private long fetchedCount;
    private long insertedCount;
    private long existingCount;
    private long conflictCount;
    private long gapCount;
    private int gapSegmentCount;
    private int batchCount;
    private BigDecimal progressPercent;
    private MarketSyncTaskStatus status;
    private String sourceMode;
    private String currentSourceFile;
    private Integer plannedFileCount;
    private int completedFileCount;
    private String errorCode;
    private String errorMessage;
    private Integer usedWeight1m;
    private Integer retryAfterSeconds;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public long getDatasetId() { return datasetId; }
    public void setDatasetId(long datasetId) { this.datasetId = datasetId; }

    public String getActiveDatasetKey() { return activeDatasetKey; }
    public void setActiveDatasetKey(String activeDatasetKey) { this.activeDatasetKey = activeDatasetKey; }

    public MarketProviderId getProvider() { return provider; }
    public void setProvider(MarketProviderId provider) { this.provider = provider; }

    public MarketType getMarketType() { return marketType; }
    public void setMarketType(MarketType marketType) { this.marketType = marketType; }

    public MarketDataType getDataType() { return dataType; }
    public void setDataType(MarketDataType dataType) { this.dataType = dataType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public KlineInterval getInterval() { return interval; }
    public void setInterval(KlineInterval interval) { this.interval = interval; }

    public Instant getRequestedStartTime() { return requestedStartTime; }
    public void setRequestedStartTime(Instant requestedStartTime) { this.requestedStartTime = requestedStartTime; }

    public Instant getRequestedEndTime() { return requestedEndTime; }
    public void setRequestedEndTime(Instant requestedEndTime) { this.requestedEndTime = requestedEndTime; }

    public Instant getNormalizedStartTime() { return normalizedStartTime; }
    public void setNormalizedStartTime(Instant normalizedStartTime) { this.normalizedStartTime = normalizedStartTime; }

    public Instant getNormalizedEndTime() { return normalizedEndTime; }
    public void setNormalizedEndTime(Instant normalizedEndTime) { this.normalizedEndTime = normalizedEndTime; }

    public long getExpectedCount() { return expectedCount; }
    public void setExpectedCount(long expectedCount) { this.expectedCount = expectedCount; }

    public long getFetchedCount() { return fetchedCount; }
    public void setFetchedCount(long fetchedCount) { this.fetchedCount = fetchedCount; }

    public long getInsertedCount() { return insertedCount; }
    public void setInsertedCount(long insertedCount) { this.insertedCount = insertedCount; }

    public long getExistingCount() { return existingCount; }
    public void setExistingCount(long existingCount) { this.existingCount = existingCount; }

    public long getConflictCount() { return conflictCount; }
    public void setConflictCount(long conflictCount) { this.conflictCount = conflictCount; }

    public long getGapCount() { return gapCount; }
    public void setGapCount(long gapCount) { this.gapCount = gapCount; }

    public int getGapSegmentCount() { return gapSegmentCount; }
    public void setGapSegmentCount(int gapSegmentCount) { this.gapSegmentCount = gapSegmentCount; }

    public int getBatchCount() { return batchCount; }
    public void setBatchCount(int batchCount) { this.batchCount = batchCount; }

    public BigDecimal getProgressPercent() { return progressPercent; }
    public void setProgressPercent(BigDecimal progressPercent) { this.progressPercent = progressPercent; }

    public MarketSyncTaskStatus getStatus() { return status; }
    public void setStatus(MarketSyncTaskStatus status) { this.status = status; }

    public String getSourceMode() { return sourceMode; }
    public void setSourceMode(String sourceMode) { this.sourceMode = sourceMode; }

    public String getCurrentSourceFile() { return currentSourceFile; }
    public void setCurrentSourceFile(String currentSourceFile) { this.currentSourceFile = currentSourceFile; }

    public Integer getPlannedFileCount() { return plannedFileCount; }
    public void setPlannedFileCount(Integer plannedFileCount) { this.plannedFileCount = plannedFileCount; }

    public int getCompletedFileCount() { return completedFileCount; }
    public void setCompletedFileCount(int completedFileCount) { this.completedFileCount = completedFileCount; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getUsedWeight1m() { return usedWeight1m; }
    public void setUsedWeight1m(Integer usedWeight1m) { this.usedWeight1m = usedWeight1m; }

    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
    public void setRetryAfterSeconds(Integer retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }

    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
