package com.aiprovider.quant.market.history.port;

import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;

import java.time.Instant;
import java.util.List;

/**
 * 同步任务仓储端口。
 *
 * 定义在 AIProvider-quant 领域层，由 AIProvider-back 使用 MyBatis 实现。
 */
public interface MarketSyncTaskRepository {

    /**
     * 插入新同步任务。
     *
     * @param task 任务
     * @return 新任务 ID
     */
    long insert(MarketSyncTask task);

    /**
     * 根据任务 ID 查找。
     *
     * @param taskId 任务 UUID
     * @return 任务，不存在返回 null
     */
    MarketSyncTask findByTaskId(String taskId);

    /**
     * 分页查询任务列表，按排队时间倒序。
     *
     * @param limit 最大返回数量
     * @param offset 偏移量
     * @return 任务列表
     */
    List<MarketSyncTask> findPage(int limit, int offset);

    /**
     * 查询非终态任务列表（用于前端轮询）。
     *
     * @return 非终态任务列表
     */
    List<MarketSyncTask> findNonTerminal();

    /**
     * 更新任务状态和进度。
     *
     * @param taskId 任务 UUID
     * @param status 新状态
     * @param fetchedCount 已获取数量
     * @param insertedCount 已新增数量
     * @param existingCount 已存在数量
     * @param conflictCount 冲突数量
     * @param gapCount 缺口数量
     * @param batchCount 批次数量
     * @param progressPercent 进度百分比
     * @param usedWeight1m 上游权重
     * @return 影响行数
     */
    int updateProgress(String taskId, MarketSyncTaskStatus status,
                        long fetchedCount, long insertedCount, long existingCount,
                        long conflictCount, long gapCount, int batchCount,
                        java.math.BigDecimal progressPercent, Integer usedWeight1m);

    /**
     * 标记任务为失败。
     *
     * @param taskId 任务 UUID
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param usedWeight1m 上游权重
     * @param retryAfterSeconds 重试等待秒数
     * @return 影响行数
     */
    int markFailed(String taskId, String errorCode, String errorMessage, Integer usedWeight1m, Integer retryAfterSeconds);

    /**
     * 标记任务为完成。
     *
     * @param taskId 任务 UUID
     * @param fetchedCount 已获取数量
     * @param insertedCount 已新增数量
     * @param existingCount 已存在数量
     * @param gapCount 缺口数量
     * @return 影响行数
     */
    int markCompleted(String taskId, long fetchedCount, long insertedCount, long existingCount, long gapCount);

    /**
     * 清除活动数据集锁。
     *
     * @param taskId 任务 UUID
     * @return 影响行数
     */
    int clearActiveLock(String taskId);

    /**
     * 查找所有非终态任务（用于服务重启恢复）。
     *
     * @return 非终态任务列表
     */
    List<MarketSyncTask> findAllNonTerminal();

    /**
     * 将非终态任务标记为服务重启中断。
     *
     * @return 影响行数
     */
    int markNonTerminalAsInterrupted();
}
