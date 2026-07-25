package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.model.KlineInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 历史行情同步服务。
 *
 * <p>从 Binance REST 分页下载历史闭合 K 线。分页下载、校验和写入委托给
 * {@link RestKlineRangeImporter}（与 AUTO 归档导入的 REST 尾部修补共用同一组件，
 * 保证游标推进和 lastOpenTime 语义一致），缺口计算委托给
 * {@link MarketTaskGapCalculator}（与归档导入共用，保证缺口统计算法唯一）。</p>
 *
 * <p>本服务只负责：任务编排、上限预检、服务器时间获取、校验阶段、缺口汇总和任务状态机。</p>
 *
 * <p>不使用 @Service 注解，由 AIProvider-back 的
 * {@link com.aiprovider.config.quant.QuantMarketHistoryConfiguration} 通过 @Bean 方式创建。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>上限预检：expectedCount 超过 maxCandlesPerTask 时快速失败，避免无效下载</li>
 *   <li>分页下载 + 校验 + 写入：委托 {@link RestKlineRangeImporter#importRange}，
 *       传入 previousLastOpenTime=-1 表示首批（修复旧实现误传当前批次 lastOpenTime 的 bug）</li>
 *   <li>缺口计算：委托 {@link MarketTaskGapCalculator#calculateTaskGapCount}，
 *       传入校验阶段检测到的内部缺口</li>
 *   <li>冲突时不自动覆盖，任务失败并记录错误码</li>
 *   <li>Binance 429/418 不自动重试，任务失败并保存 Retry-After</li>
 * </ul>
 */
public class MarketHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistorySyncService.class);
    private static final String ERR_MAX_CANDLES_EXCEEDED = "MAX_CANDLES_EXCEEDED";
    private static final String ERR_UNKNOWN = "UNKNOWN_ERROR";

    private final HistoricalMarketDataProvider provider;
    private final RestKlineRangeImporter restImporter;
    private final MarketTaskGapCalculator gapCalculator;
    private final MarketDatasetRepository datasetRepository;
    private final MarketSyncTaskRepository taskRepository;
    private final MarketDatasetValidationService validationService;
    private final int maxCandlesPerTask;

    public MarketHistorySyncService(HistoricalMarketDataProvider provider,
                                     RestKlineRangeImporter restImporter,
                                     MarketTaskGapCalculator gapCalculator,
                                     MarketDatasetRepository datasetRepository,
                                     MarketSyncTaskRepository taskRepository,
                                     MarketDatasetValidationService validationService,
                                     int maxCandlesPerTask) {
        this.provider = provider;
        this.restImporter = restImporter;
        this.gapCalculator = gapCalculator;
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
        this.validationService = validationService;
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
        long normalizedStart = task.getNormalizedStartTime().toEpochMilli();
        long normalizedEnd = task.getNormalizedEndTime().toEpochMilli();
        long expectedCount = task.getExpectedCount();

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
            log.info("operation=sync-start taskId={} datasetId={} symbol={} interval={} normalizedStart={} normalizedEnd={} expectedCount={} serverTime={}",
                    taskId, task.getDatasetId(), task.getSymbol(), interval.code(),
                    normalizedStart, normalizedEnd, expectedCount, serverTime);

            taskRepository.updateProgress(taskId, MarketSyncTaskStatus.DOWNLOADING,
                    0, 0, 0, 0, 0, 0, BigDecimal.ZERO, null);

            // 委托 RestKlineRangeImporter 执行分页下载 + 校验 + 写入
            // （含游标推进校验、previousLastOpenTime 语义、进度更新和已获取数量上限校验）
            RestKlineRangeImporter.ImportStats stats = restImporter.importRange(
                    task, normalizedStart, normalizedEnd, serverTimeMs, -1);

            // 校验阶段
            taskRepository.updateProgress(taskId, MarketSyncTaskStatus.VALIDATING,
                    stats.fetched, stats.inserted, stats.existing, stats.conflict, 0,
                    stats.batches, BigDecimal.valueOf(100), usedWeight1m);

            MarketDatasetValidationService.ValidationResult validationResult =
                    validationService.validateDataset(task.getDatasetId(), interval, taskId);

            // 委托 MarketTaskGapCalculator 计算请求范围内的缺口（前部 + 内部 + 后部）
            long taskGapCount = gapCalculator.calculateTaskGapCount(interval, normalizedStart, normalizedEnd,
                    validationResult.earliestOpenTimeMs, validationResult.latestOpenTimeMs,
                    validationResult.gaps);

            // 标记完成（含缺口区段数）
            taskRepository.markCompleted(taskId, stats.fetched, stats.inserted, stats.existing,
                    taskGapCount, validationResult.gapSegmentCount);

            // 更新数据集最后同步信息
            datasetRepository.updateLastSync(task.getDatasetId(), taskId, Instant.now());

            log.info("operation=sync-complete taskId={} datasetId={} fetched={} inserted={} existing={} conflict={} gaps={} gapSegments={} batches={}",
                    taskId, task.getDatasetId(), stats.fetched, stats.inserted, stats.existing, stats.conflict,
                    taskGapCount, validationResult.gapSegmentCount, stats.batches);

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

        } catch (RestKlineRangeImporter.RestImportException e) {
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
