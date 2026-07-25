package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import com.aiprovider.quant.market.history.model.MarketDataGap;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * 历史行情同步服务。
 *
 * 从 Binance REST 分页下载历史闭合 K 线，通过 {@link MarketCandleIngestService} 统一校验并写入数据库。
 * 每批操作在独立事务内完成，中途失败时已提交的 K 线保留。
 *
 * 不使用 @Service 注解，由 AIProvider-back 的
 * {@link com.aiprovider.config.quant.QuantMarketHistoryConfiguration} 通过 @Bean 方式创建，
 * 注入 batchSize 和 maxCandlesPerTask 配置参数。
 *
 * 核心设计：
 * <ul>
 *   <li>分页游标严格使用最后一根已验证 K 线的 openTime + duration 推进</li>
 *   <li>每页校验委托给 {@link MarketCandleIngestService#ingestBatch}，与 ZIP 归档导入共用同一管线</li>
 *   <li>冲突时不自动覆盖，任务失败并记录错误码</li>
 *   <li>Binance 429/418 不自动重试，任务失败并保存 Retry-After</li>
 *   <li>空页结束下载，进入缺口校验</li>
 * </ul>
 */
public class MarketHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistorySyncService.class);
    private static final String SOURCE = "BINANCE_USDM_REST";
    private static final String ERR_CURSOR_STALL = "CURSOR_NOT_ADVANCING";
    private static final String ERR_MAX_CANDLES_EXCEEDED = "MAX_CANDLES_EXCEEDED";
    private static final String ERR_UNKNOWN = "UNKNOWN_ERROR";

    private final HistoricalMarketDataProvider provider;
    private final MarketCandleIngestService ingestService;
    private final MarketDatasetRepository datasetRepository;
    private final MarketSyncTaskRepository taskRepository;
    private final MarketDatasetValidationService validationService;

    private final int batchSize;
    private final int maxCandlesPerTask;

    public MarketHistorySyncService(HistoricalMarketDataProvider provider,
                                     MarketCandleIngestService ingestService,
                                     MarketDatasetRepository datasetRepository,
                                     MarketSyncTaskRepository taskRepository,
                                     MarketDatasetValidationService validationService,
                                     int batchSize, int maxCandlesPerTask) {
        this.provider = provider;
        this.ingestService = ingestService;
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
        this.validationService = validationService;
        this.batchSize = batchSize;
        this.maxCandlesPerTask = maxCandlesPerTask;
    }

    /**
     * 执行同步任务。
     *
     * 该方法在执行器线程中调用，不在 HTTP 请求线程中执行。
     * 中途失败时已提交的 K 线保留，任务标记为 FAILED。
     *
     * @param task 同步任务（已持久化到数据库）
     */
    public void executeSync(MarketSyncTask task) {
        String taskId = task.getTaskId();
        KlineInterval interval = task.getInterval();
        long durationMs = interval.durationMillis();
        long normalizedStart = task.getNormalizedStartTime().toEpochMilli();
        long normalizedEnd = task.getNormalizedEndTime().toEpochMilli();
        long expectedCount = task.getExpectedCount();

        long fetchedCount = 0;
        long insertedCount = 0;
        long existingCount = 0;
        long conflictCount = 0;
        int batchCount = 0;
        Integer usedWeight1m = null;

        try {
            // 单任务 K 线数量上限校验，快速失败避免无效下载
            if (expectedCount > maxCandlesPerTask) {
                throw new SyncException(ERR_MAX_CANDLES_EXCEEDED,
                        "任务预期 K 线数量超过上限: expected=" + expectedCount + " max=" + maxCandlesPerTask);
            }

            // 获取上游服务器时间，排除未闭合 K 线
            Instant serverTime = provider.serverTime();
            long serverTimeMs = serverTime.toEpochMilli();
            log.info("operation=sync-start taskId={} datasetId={} symbol={} interval={} normalizedStart={} normalizedEnd={} expectedCount={} serverTime={} batchSize={}",
                    taskId, task.getDatasetId(), task.getSymbol(), interval.code(),
                    normalizedStart, normalizedEnd, expectedCount, serverTime, batchSize);

            taskRepository.updateProgress(taskId, MarketSyncTaskStatus.DOWNLOADING,
                    0, 0, 0, 0, 0, 0, BigDecimal.ZERO, null);

            long cursor = normalizedStart;
            long lastOpenTime = -1;

            while (cursor < normalizedEnd) {
                // 计算本批结束时间（不超过 normalizedEnd）
                long batchEnd = Math.min(cursor + (long) batchSize * durationMs, normalizedEnd);

                // 从 Binance 获取 K 线
                List<MarketCandle> fetched = provider.fetchClosedKlines(
                        task.getSymbol(), interval, cursor, batchEnd, batchSize);

                if (fetched == null || fetched.isEmpty()) {
                    // 空页，结束下载
                    log.info("operation=sync-batch taskId={} batch={} msg=空页结束下载 cursor={} batchEnd={}",
                            taskId, batchCount, cursor, batchEnd);
                    break;
                }

                // 游标推进校验（REST 专有，IngestService 不负责）
                long batchLastOpenTime = fetched.get(fetched.size() - 1).getOpenTime().toEpochMilli();
                if (batchLastOpenTime <= lastOpenTime) {
                    throw new SyncException(ERR_CURSOR_STALL,
                            "分页游标未推进: lastOpenTime=" + batchLastOpenTime + " cursor=" + cursor);
                }
                lastOpenTime = batchLastOpenTime;

                // 委托 IngestService 执行校验 + 事务写入（与 ZIP 归档共用同一管线）
                MarketCandleIngestService.BatchResult batchResult = ingestService.ingestBatch(
                        task.getDatasetId(), fetched, interval, task.getSymbol(),
                        cursor, normalizedEnd, serverTimeMs, lastOpenTime, SOURCE);

                // 累加统计
                fetchedCount += fetched.size();
                insertedCount += batchResult.inserted;
                existingCount += batchResult.existing;
                conflictCount += batchResult.conflict;
                batchCount++;

                // 推进游标
                cursor = lastOpenTime + durationMs;

                // 计算进度
                BigDecimal progress = calculateProgress(cursor, normalizedStart, normalizedEnd);
                taskRepository.updateProgress(taskId, MarketSyncTaskStatus.WRITING,
                        fetchedCount, insertedCount, existingCount, conflictCount, 0,
                        batchCount, progress, usedWeight1m);

                log.debug("operation=sync-batch taskId={} batch={} fetched={} inserted={} existing={} conflict={} cursor={} progress={}",
                        taskId, batchCount, fetched.size(), batchResult.inserted, batchResult.existing,
                        batchResult.conflict, cursor, progress);
            }

            // 校验阶段
            taskRepository.updateProgress(taskId, MarketSyncTaskStatus.VALIDATING,
                    fetchedCount, insertedCount, existingCount, conflictCount, 0,
                    batchCount, BigDecimal.valueOf(100), usedWeight1m);

            MarketDatasetValidationService.ValidationResult validationResult =
                    validationService.validateDataset(task.getDatasetId(), interval, taskId);

            // 计算请求范围内的缺口（前部 + 内部 + 后部）
            long taskGapCount = calculateTaskGaps(interval, normalizedStart, normalizedEnd,
                    validationResult.earliestOpenTimeMs, validationResult.latestOpenTimeMs,
                    validationResult.gaps);

            // 标记完成（含缺口区段数）
            taskRepository.markCompleted(taskId, fetchedCount, insertedCount, existingCount,
                    taskGapCount, validationResult.gapSegmentCount);

            // 更新数据集最后同步信息
            datasetRepository.updateLastSync(task.getDatasetId(), taskId, Instant.now());

            log.info("operation=sync-complete taskId={} datasetId={} fetched={} inserted={} existing={} conflict={} gaps={} gapSegments={} batches={}",
                    taskId, task.getDatasetId(), fetchedCount, insertedCount, existingCount, conflictCount,
                    taskGapCount, validationResult.gapSegmentCount, batchCount);

        } catch (BinanceUsdmUpstreamException e) {
            String errorCode = "UPSTREAM_ERROR";
            if (e.getHttpStatus() == 429 || e.getHttpStatus() == 418) {
                errorCode = "BINANCE_RATE_LIMIT";
            }
            String errorMsg = "Binance 上游失败: httpStatus=" + e.getHttpStatus()
                    + " errorCode=" + e.getErrorCode()
                    + (e.getErrorMsg() != null ? " msg=" + e.getErrorMsg() : "");
            if (e.getUsedWeight1m() != null) {
                try {
                    usedWeight1m = Integer.parseInt(e.getUsedWeight1m());
                } catch (NumberFormatException ignored) {}
            }
            taskRepository.markFailed(taskId, errorCode, truncateMsg(errorMsg), usedWeight1m, e.getRetryAfter());
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=sync-failed taskId={} errorCode={} httpStatus={} retryAfter={} usedWeight={}",
                    taskId, errorCode, e.getHttpStatus(), e.getRetryAfter(), usedWeight1m);

        } catch (MarketCandleIngestService.IngestException e) {
            taskRepository.markFailed(taskId, e.errorCode, truncateMsg(e.getMessage()), usedWeight1m, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=sync-failed taskId={} errorCode={} msg={}",
                    taskId, e.errorCode, e.getMessage());

        } catch (SyncException e) {
            taskRepository.markFailed(taskId, e.errorCode, truncateMsg(e.getMessage()), usedWeight1m, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=sync-failed taskId={} errorCode={} msg={}",
                    taskId, e.errorCode, e.getMessage());

        } catch (Exception e) {
            taskRepository.markFailed(taskId, ERR_UNKNOWN, truncateMsg(e.getMessage()), usedWeight1m, null);
            taskRepository.clearActiveLock(taskId);
            log.error("operation=sync-failed taskId={} errorCode={} msg={}",
                    taskId, ERR_UNKNOWN, e.getMessage(), e);
        }
    }

    // ---- 辅助方法 ----

    private BigDecimal calculateProgress(long cursor, long start, long end) {
        if (end <= start) return BigDecimal.valueOf(100);
        long total = end - start;
        long done = Math.min(cursor - start, total);
        return BigDecimal.valueOf(done)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    /**
     * 计算任务请求范围内的缺口 K 线总根数。
     *
     * 缺口由三部分组成：
     * <ol>
     *   <li>前部缺口：normalizedStart 到第一根 K 线 openTime 之间</li>
     *   <li>内部缺口：数据集内已检测到的 gap 区段，与任务请求范围取交集</li>
     *   <li>后部缺口：最后一根 K 线 closeTime 到 normalizedEnd 之间</li>
     * </ol>
     *
     * @param interval       K 线周期
     * @param normalizedStart 任务归一化起始时间（ms）
     * @param normalizedEnd   任务归一化结束时间（ms）
     * @param earliestMs      数据集最早 K 线 openTime（ms），null 表示无数据
     * @param latestMs        数据集最晚 K 线 openTime（ms），null 表示无数据
     * @param internalGaps    校验阶段检测到的内部缺口列表
     * @return 任务请求范围内的缺口 K 线总根数
     */
    private long calculateTaskGaps(KlineInterval interval,
                                    long normalizedStart, long normalizedEnd,
                                    Long earliestMs, Long latestMs,
                                    List<MarketDataGap> internalGaps) {
        long durationMs = interval.durationMillis();

        if (earliestMs == null || latestMs == null) {
            return (normalizedEnd - normalizedStart) / durationMs;
        }

        long gaps = 0;

        long frontEnd = Math.min(earliestMs, normalizedEnd);
        if (frontEnd > normalizedStart) {
            gaps += (frontEnd - normalizedStart) / durationMs;
        }

        for (MarketDataGap gap : internalGaps) {
            long gapStart = gap.getStartOpenTime().toEpochMilli();
            long gapEndExclusive = gap.getEndOpenTimeExclusive().toEpochMilli();
            long intersectStart = Math.max(gapStart, normalizedStart);
            long intersectEnd = Math.min(gapEndExclusive, normalizedEnd);
            if (intersectEnd > intersectStart) {
                gaps += (intersectEnd - intersectStart) / durationMs;
            }
        }

        long backStart = Math.max(latestMs + durationMs, normalizedStart);
        if (normalizedEnd > backStart) {
            gaps += (normalizedEnd - backStart) / durationMs;
        }

        return gaps;
    }

    private static String truncateMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    // ---- 内部类型 ----

    private static class SyncException extends RuntimeException {
        final String errorCode;
        SyncException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
