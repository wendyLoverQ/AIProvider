package com.aiprovider.repository.quant;

import com.aiprovider.mapper.MarketSyncTaskMapper;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link MarketSyncTaskRepository} 的 MyBatis 实现。
 *
 * Mapper 的 updateProgress 接受 String status，本实现将端口层的
 * {@link MarketSyncTaskStatus} 转换为 name() 字符串。
 * findNonTerminal 与 findAllNonTerminal 均委托 mapper 的 findAllNonTerminal，
 * 查询非终态任务列表（单 worker + 有界队列场景下非终态任务数量有限）。
 */
@Repository
public class MarketSyncTaskRepositoryImpl implements MarketSyncTaskRepository {

    private final MarketSyncTaskMapper mapper;

    public MarketSyncTaskRepositoryImpl(MarketSyncTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long insert(MarketSyncTask task) {
        mapper.insert(task);
        return task.getId();
    }

    @Override
    public MarketSyncTask findByTaskId(String taskId) {
        return mapper.findByTaskId(taskId);
    }

    @Override
    public List<MarketSyncTask> findPage(int limit, int offset) {
        return mapper.findPage(limit, offset);
    }

    @Override
    public List<MarketSyncTask> findNonTerminal() {
        return mapper.findAllNonTerminal();
    }

    @Override
    public List<MarketSyncTask> findAllNonTerminal() {
        return mapper.findAllNonTerminal();
    }

    @Override
    public int updateProgress(String taskId, MarketSyncTaskStatus status,
                              long fetchedCount, long insertedCount, long existingCount,
                              long conflictCount, long gapCount, int batchCount,
                              BigDecimal progressPercent, Integer usedWeight1m) {
        return mapper.updateProgress(taskId, status.name(),
                fetchedCount, insertedCount, existingCount,
                conflictCount, gapCount, batchCount,
                progressPercent, usedWeight1m);
    }

    @Override
    public int markFailed(String taskId, String errorCode, String errorMessage,
                          Integer usedWeight1m, Integer retryAfterSeconds) {
        return mapper.markFailed(taskId, errorCode, errorMessage, usedWeight1m, retryAfterSeconds);
    }

    @Override
    public int markCompleted(String taskId, long fetchedCount, long insertedCount,
                              long existingCount, long gapCount, int gapSegmentCount) {
        return mapper.markCompleted(taskId, fetchedCount, insertedCount, existingCount, gapCount, gapSegmentCount);
    }

    @Override
    public int updateArchiveProgress(String taskId, String status,
                                      String sourceMode, String currentSourceFile,
                                      Integer plannedFileCount, int completedFileCount,
                                      long fetchedCount, long insertedCount, long existingCount,
                                      long conflictCount, int batchCount,
                                      BigDecimal progressPercent) {
        return mapper.updateArchiveProgress(taskId, status, sourceMode, currentSourceFile,
                plannedFileCount, completedFileCount, fetchedCount, insertedCount,
                existingCount, conflictCount, batchCount, progressPercent);
    }

    @Override
    public int clearActiveLock(String taskId) {
        return mapper.clearActiveLock(taskId);
    }

    @Override
    public int markNonTerminalAsInterrupted() {
        return mapper.markNonTerminalAsInterrupted();
    }
}
