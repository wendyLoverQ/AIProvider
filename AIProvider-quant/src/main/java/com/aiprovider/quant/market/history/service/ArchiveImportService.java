package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import com.aiprovider.quant.market.history.model.ArchiveImportMode;
import com.aiprovider.quant.market.history.model.ArchiveImportPlan;
import com.aiprovider.quant.market.history.model.ArchiveKlineFile;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.HistoricalArchiveProvider;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Binance 官方 ZIP 归档导入服务。
 *
 * 编排 {@link ArchivePlanner}（计划下载文件）、{@link HistoricalArchiveProvider}（下载+解析）
 * 和 {@link MarketCandleIngestService}（校验+写入），完成历史 K 线的批量导入。
 *
 * <p>不使用 @Service 注解，由 AIProvider-back 通过 @Bean 创建。
 * 与 {@link MarketHistorySyncService} 共用 {@link MarketCandleIngestService} 写入管线，
 * 保证 REST 和归档两条数据来源走同一套校验和冲突检测逻辑。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>归档数据天然闭合，serverTimeMs 传 0 跳过闭合校验</li>
 *   <li>每个 ZIP 文件覆盖 [rangeStart, rangeEndExclusive)，CSV 可能包含范围外 K 线，需过滤</li>
 *   <li>lastOpenTime 跨文件累加，保证全局升序校验</li>
 *   <li>进度通过 updateArchiveProgress 跟踪，含 sourceMode/currentSourceFile/fileCount</li>
 *   <li>AUTO 模式在同一任务内完成归档月包/日包 + REST 尾部修补：
 *       归档文件下载写入后，委托 {@link RestKlineRangeImporter#importRange} 修补
 *       [restTailStart, restTailEnd) 范围，不要求前端创建第二个任务</li>
 *   <li>非 AUTO 模式（ARCHIVE_MONTHLY/ARCHIVE_DAILY）只导入归档包，跳过 REST 尾部</li>
 *   <li>冲突不自动覆盖，任务失败并记录错误码</li>
 * </ul>
 */
public class ArchiveImportService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveImportService.class);
    private static final String SOURCE = "BINANCE_ARCHIVE";
    private static final String ERR_UNKNOWN = "ARCHIVE_IMPORT_ERROR";

    private final ArchivePlanner planner;
    private final HistoricalArchiveProvider archiveProvider;
    private final MarketCandleIngestService ingestService;
    private final RestKlineRangeImporter restImporter;
    private final HistoricalMarketDataProvider marketDataProvider;
    private final MarketDatasetRepository datasetRepository;
    private final MarketSyncTaskRepository taskRepository;
    private final MarketDatasetValidationService validationService;
    private final MarketTaskGapCalculator gapCalculator;

    public ArchiveImportService(ArchivePlanner planner,
                                HistoricalArchiveProvider archiveProvider,
                                MarketCandleIngestService ingestService,
                                RestKlineRangeImporter restImporter,
                                HistoricalMarketDataProvider marketDataProvider,
                                MarketDatasetRepository datasetRepository,
                                MarketSyncTaskRepository taskRepository,
                                MarketDatasetValidationService validationService,
                                MarketTaskGapCalculator gapCalculator) {
        this.planner = planner;
        this.archiveProvider = archiveProvider;
        this.ingestService = ingestService;
        this.restImporter = restImporter;
        this.marketDataProvider = marketDataProvider;
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
        this.validationService = validationService;
        this.gapCalculator = gapCalculator;
    }

    /**
     * 执行归档导入任务。
     *
     * 该方法在执行器线程中调用，不在 HTTP 请求线程中执行。
     * 中途失败时已写入的 K 线保留，任务标记为 FAILED。
     *
     * @param task 已持久化的同步任务（datasetId、symbol、interval、normalizedRange 已就绪）
     */
    public void executeArchiveImport(MarketSyncTask task) {
        String taskId = task.getTaskId();
        KlineInterval interval = task.getInterval();
        long normalizedStart = task.getNormalizedStartTime().toEpochMilli();
        long normalizedEnd = task.getNormalizedEndTime().toEpochMilli();
        long serverTimeMs;

        try {
            serverTimeMs = marketDataProvider.serverTime().toEpochMilli();
            // 1. 规划归档文件
            ArchiveImportMode requestedMode = ArchiveImportMode.valueOf(task.getSourceMode());
            ArchiveImportPlan plan = planner.plan(task.getSymbol(), interval, normalizedStart, normalizedEnd,
                    serverTimeMs, requestedMode);

            int plannedFileCount = plan.totalFileCount();
            ImportStats stats = new ImportStats();
            stats.plannedFileCount = plannedFileCount;

            log.info("operation=archive-import-start taskId={} datasetId={} symbol={} interval={} plannedFiles={} monthly={} daily={} hasRestTail={}",
                    taskId, task.getDatasetId(), task.getSymbol(), interval.code(),
                    plannedFileCount, plan.getMonthlyFileCount(), plan.getDailyFileCount(), plan.isHasRestTail());

            // 2. 逐文件下载 + 解析 + 写入（归档部分）
            for (int i = 0; i < plan.getFiles().size(); i++) {
                ArchiveKlineFile file = plan.getFiles().get(i);

                // 更新进度：DOWNLOADING
                BigDecimal progress = calculateArchiveProgress(i, plannedFileCount, plan.isHasRestTail());
                taskRepository.updateArchiveProgress(taskId, MarketSyncTaskStatus.DOWNLOADING.name(),
                        plan.getMode().name(), file.getZipFileName(),
                        plannedFileCount, i,
                        stats.fetched, stats.inserted, stats.existing, stats.conflict,
                        stats.batches, progress);

                // 下载 + 解析 + 写入（回调内过滤范围并调用 IngestService）
                final long fileRangeStart = file.getRangeStart();
                final long fileRangeEnd = file.getRangeEndExclusive();

                archiveProvider.downloadAndParse(file, task.getSymbol(), interval, new Consumer<List<MarketCandle>>() {
                    @Override
                    public void accept(List<MarketCandle> batch) {
                        // 过滤到文件目标范围（日包可能只覆盖部分天数）
                        List<MarketCandle> filtered = new ArrayList<>(batch.size());
                        for (MarketCandle c : batch) {
                            long openTimeMs = c.getOpenTime().toEpochMilli();
                            if (openTimeMs >= fileRangeStart && openTimeMs < fileRangeEnd) {
                                filtered.add(c);
                            }
                        }
                        if (filtered.isEmpty()) {
                            return;
                        }

                        // 委托 IngestService 校验 + 事务写入
                        MarketCandleIngestService.BatchResult result = ingestService.ingestBatch(
                                task.getDatasetId(), filtered, interval, task.getSymbol(),
                                fileRangeStart, fileRangeEnd, 0, stats.lastOpenTime, SOURCE);

                        // 累加统计
                        stats.fetched += filtered.size();
                        stats.inserted += result.inserted;
                        stats.existing += result.existing;
                        stats.conflict += result.conflict;
                        stats.batches++;
                        stats.lastOpenTime = filtered.get(filtered.size() - 1).getOpenTime().toEpochMilli();
                    }
                });

                // 文件完成，更新进度：WRITING
                progress = calculateArchiveProgress(i + 1, plannedFileCount, plan.isHasRestTail());
                taskRepository.updateArchiveProgress(taskId, MarketSyncTaskStatus.WRITING.name(),
                        plan.getMode().name(), file.getZipFileName(),
                        plannedFileCount, i + 1,
                        stats.fetched, stats.inserted, stats.existing, stats.conflict,
                        stats.batches, progress);
                stats.completedFileCount = i + 1;

                log.info("operation=archive-import-file-complete taskId={} file={} completed={} fetched={} inserted={} existing={} progress={}",
                        taskId, file.getZipFileName(), i + 1, stats.fetched, stats.inserted, stats.existing, progress);
            }

            // 3. REST 尾部修补（仅 AUTO 模式，在同一任务内完成归档 + REST 尾部）
            //    非 AUTO 模式（ARCHIVE_MONTHLY/ARCHIVE_DAILY）只导入归档包，跳过 REST 尾部。
            if (plan.isHasRestTail() && ArchiveImportMode.AUTO.name().equals(task.getSourceMode())) {
                long restTailStart = plan.getRestTailStartInclusive();
                long restTailEnd = plan.getRestTailEndExclusive();

                log.info("operation=archive-import-rest-tail-start taskId={} restTailStart={} restTailEnd={} lastOpenTime={}",
                        taskId, restTailStart, restTailEnd, stats.lastOpenTime);

                // 获取上游服务器时间，用于闭合校验（排除未闭合 K 线）
                RestKlineRangeImporter.ImportStats restStats = restImporter.importRange(
                        task, restTailStart, restTailEnd, serverTimeMs, stats.lastOpenTime,
                        current -> taskRepository.updateArchiveProgress(taskId,
                                MarketSyncTaskStatus.WRITING.name(), task.getSourceMode(), null,
                                stats.plannedFileCount, stats.completedFileCount,
                                stats.fetched + current.fetched, stats.inserted + current.inserted,
                                stats.existing + current.existing, stats.conflict + current.conflict,
                                stats.batches + current.batches,
                                calculateRestOverallProgress(current.progressPercent, plan.isHasRestTail(), plannedFileCount)));

                stats.fetched += restStats.fetched;
                stats.inserted += restStats.inserted;
                stats.existing += restStats.existing;
                stats.conflict += restStats.conflict;
                stats.batches += restStats.batches;
                stats.lastOpenTime = restStats.lastOpenTime;

                log.info("operation=archive-import-rest-tail-complete taskId={} fetched={} inserted={} existing={} conflict={}",
                        taskId, restStats.fetched, restStats.inserted, restStats.existing, restStats.conflict);
            }

            // 4. 校验 + 完成
            completeWithValidation(task, interval, normalizedStart, normalizedEnd, stats);

        } catch (MarketCandleIngestService.IngestException e) {
            taskRepository.markFailed(taskId, e.errorCode, truncateMsg(e.getMessage()), null, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=archive-import-failed taskId={} errorCode={} msg={}",
                    taskId, e.errorCode, e.getMessage());

        } catch (ArchiveDataException e) {
            taskRepository.markFailed(taskId, e.getErrorCode(), truncateMsg(e.getMessage()), null, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=archive-import-failed taskId={} errorCode={} msg={}",
                    taskId, e.getErrorCode(), e.getMessage());

        } catch (RestKlineRangeImporter.RestImportException e) {
            taskRepository.markFailed(taskId, e.errorCode, truncateMsg(e.getMessage()), null, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=archive-import-failed taskId={} errorCode={} msg={}",
                    taskId, e.errorCode, e.getMessage());

        } catch (BinanceUsdmUpstreamException e) {
            String errorCode = "UPSTREAM_ERROR";
            if (e.getHttpStatus() == 429 || e.getHttpStatus() == 418) {
                errorCode = "BINANCE_RATE_LIMIT";
            }
            String errorMsg = "Binance 上游失败(rest-tail): httpStatus=" + e.getHttpStatus()
                    + " errorCode=" + e.getErrorCode()
                    + (e.getErrorMsg() != null ? " msg=" + e.getErrorMsg() : "");
            Integer usedWeight1m = null;
            if (e.getUsedWeight1m() != null) {
                try {
                    usedWeight1m = Integer.parseInt(e.getUsedWeight1m());
                } catch (NumberFormatException ignored) {}
            }
            taskRepository.markFailed(taskId, errorCode, truncateMsg(errorMsg), usedWeight1m, e.getRetryAfter());
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=archive-import-failed taskId={} errorCode={} httpStatus={} retryAfter={}",
                    taskId, errorCode, e.getHttpStatus(), e.getRetryAfter());

        } catch (Exception e) {
            taskRepository.markFailed(taskId, ERR_UNKNOWN, truncateMsg(e.getMessage()), null, null);
            taskRepository.clearActiveLock(taskId);
            log.error("operation=archive-import-failed taskId={} msg={}", taskId, e.getMessage(), e);
        }
    }

    // ---- 校验与完成 ----

    private void completeWithValidation(MarketSyncTask task, KlineInterval interval,
                                        long normalizedStart, long normalizedEnd,
                                        ImportStats stats) {
        String taskId = task.getTaskId();

        taskRepository.updateArchiveProgress(taskId, MarketSyncTaskStatus.VALIDATING.name(),
                task.getSourceMode(), null, stats.plannedFileCount, stats.completedFileCount,
                stats.fetched, stats.inserted, stats.existing, stats.conflict,
                stats.batches, BigDecimal.valueOf(99));

        MarketDatasetValidationService.ValidationResult vr =
                validationService.validateDataset(task.getDatasetId(), interval, taskId);

        long taskGapCount = gapCalculator.calculateTaskGapCount(interval, normalizedStart, normalizedEnd,
                vr.earliestOpenTimeMs, vr.latestOpenTimeMs, vr.gaps);

        taskRepository.markCompleted(taskId, stats.fetched, stats.inserted, stats.existing,
                taskGapCount, vr.gapSegmentCount);

        datasetRepository.updateLastSync(task.getDatasetId(), taskId, Instant.now());

        log.info("operation=archive-import-complete taskId={} datasetId={} fetched={} inserted={} existing={} conflict={} gaps={} gapSegments={} batches={}",
                taskId, task.getDatasetId(), stats.fetched, stats.inserted, stats.existing,
                stats.conflict, taskGapCount, vr.gapSegmentCount, stats.batches);
    }

    // ---- 辅助方法 ----

    private BigDecimal calculateArchiveProgress(int completedFiles, int totalFiles, boolean hasRestTail) {
        if (totalFiles <= 0) return BigDecimal.ZERO;
        BigDecimal ceiling = hasRestTail ? BigDecimal.valueOf(90) : BigDecimal.valueOf(99);
        return BigDecimal.valueOf(completedFiles)
                .multiply(ceiling)
                .divide(BigDecimal.valueOf(totalFiles), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRestOverallProgress(BigDecimal restProgress,
                                                    boolean hasArchive,
                                                    int plannedFileCount) {
        BigDecimal bounded = restProgress.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        if (!hasArchive || plannedFileCount == 0) {
            return bounded.multiply(BigDecimal.valueOf(99))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(90).add(bounded.multiply(BigDecimal.valueOf(9))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    private static String truncateMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    // ---- 内部类型 ----

    /** 跨文件累加的导入统计。 */
    private static class ImportStats {
        long fetched = 0;
        long inserted = 0;
        long existing = 0;
        long conflict = 0;
        int batches = 0;
        long lastOpenTime = -1;
        int plannedFileCount = 0;
        int completedFileCount = 0;
    }
}
