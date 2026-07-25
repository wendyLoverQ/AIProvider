package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可复用 REST K 线范围导入器。
 *
 * REST 同步和 AUTO 归档导入的 REST 尾部修补共用此组件，
 * 保证分页游标推进和 lastOpenTime 语义一致。
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>previousLastOpenTime 初始为 -1（首批），后续为上一批最后 openTime</li>
 *   <li>每批先保存 previousLastOpenTime，再更新 currentBatchLastOpenTime</li>
 *   <li>传给 IngestService 的是 previousLastOpenTime，不是当前批次最后 openTime</li>
 *   <li>游标严格使用当前批次最后 openTime + duration 推进</li>
 * </ul>
 */
public class RestKlineRangeImporter {

    private static final Logger log = LoggerFactory.getLogger(RestKlineRangeImporter.class);
    private static final String SOURCE = "BINANCE_USDM_REST";
    private static final String ERR_CURSOR_STALL = "CURSOR_NOT_ADVANCING";
    private static final String ERR_MAX_CANDLES_EXCEEDED = "MAX_CANDLES_EXCEEDED";

    private final HistoricalMarketDataProvider provider;
    private final MarketCandleIngestService ingestService;
    private final int batchSize;
    private final int maxCandlesPerTask;

    public RestKlineRangeImporter(HistoricalMarketDataProvider provider,
                                   MarketCandleIngestService ingestService,
                                   int batchSize, int maxCandlesPerTask) {
        this.provider = provider;
        this.ingestService = ingestService;
        this.batchSize = batchSize;
        this.maxCandlesPerTask = maxCandlesPerTask;
    }

    /** Compatibility constructor retained for existing callers; the repository is intentionally ignored. */
    @Deprecated
    public RestKlineRangeImporter(HistoricalMarketDataProvider provider,
                                   MarketCandleIngestService ingestService,
                                   com.aiprovider.quant.market.history.port.MarketSyncTaskRepository ignored,
                                   int batchSize, int maxCandlesPerTask) {
        this(provider, ingestService, batchSize, maxCandlesPerTask);
    }

    /**
     * 导入指定时间范围的闭合 K 线。
     *
     * @param task            同步任务（用于获取 datasetId、symbol、interval 等）
     * @param rangeStartInclusive 范围起始（包含），epoch 毫秒
     * @param rangeEndExclusive   范围结束（不包含），epoch 毫秒
     * @param serverTimeMs    上游服务器时间，用于闭合校验
     * @param initialLastOpenTime 初始 previousLastOpenTime（-1 表示从头开始，或从已有数据继续）
     * @return 导入统计
     */
    public ImportStats importRange(MarketSyncTask task, long rangeStartInclusive, long rangeEndExclusive,
                                    long serverTimeMs, long initialLastOpenTime) {
        return importRange(task, rangeStartInclusive, rangeEndExclusive, serverTimeMs,
                initialLastOpenTime, null);
    }

    public ImportStats importRange(MarketSyncTask task, long rangeStartInclusive, long rangeEndExclusive,
                                    long serverTimeMs, long initialLastOpenTime,
                                    Consumer<ImportStats> progressCallback) {
        String taskId = task.getTaskId();
        KlineInterval interval = task.getInterval();
        long durationMs = interval.durationMillis();

        ImportStats stats = new ImportStats();
        stats.lastOpenTime = initialLastOpenTime;

        long cursor = rangeStartInclusive;

        while (cursor < rangeEndExclusive) {
            long batchEnd = Math.min(cursor + (long) batchSize * durationMs, rangeEndExclusive);

            List<MarketCandle> fetched = provider.fetchClosedKlines(
                    task.getSymbol(), interval, cursor, batchEnd, batchSize, serverTimeMs);

            if (fetched == null || fetched.isEmpty()) {
                log.info("operation=rest-import-batch taskId={} msg=空页结束 cursor={} batchEnd={}",
                        taskId, cursor, batchEnd);
                break;
            }

            // 保存上一批最后 openTime（修复 P1：传给 IngestService 的是 previousLastOpenTime）
            long previousLastOpenTime = stats.lastOpenTime;
            long currentBatchLastOpenTime = fetched.get(fetched.size() - 1).getOpenTime().toEpochMilli();

            if (currentBatchLastOpenTime <= previousLastOpenTime) {
                throw new RestImportException(ERR_CURSOR_STALL,
                        "分页游标未推进: currentBatchLastOpenTime=" + currentBatchLastOpenTime
                                + " previousLastOpenTime=" + previousLastOpenTime);
            }

            // 委托 IngestService 校验 + 事务写入（传入 previousLastOpenTime，不是 currentBatchLastOpenTime）
            MarketCandleIngestService.BatchResult batchResult = ingestService.ingestBatch(
                    task.getDatasetId(), fetched, interval, task.getSymbol(),
                    cursor, rangeEndExclusive, serverTimeMs, previousLastOpenTime, SOURCE);

            stats.fetched += fetched.size();
            stats.inserted += batchResult.inserted;
            stats.existing += batchResult.existing;
            stats.conflict += batchResult.conflict;
            stats.batches++;

            // 单任务已获取 K 线数量上限校验
            if (stats.fetched > maxCandlesPerTask) {
                throw new RestImportException(ERR_MAX_CANDLES_EXCEEDED,
                        "任务已获取 K 线数量超过上限: fetched=" + stats.fetched + " max=" + maxCandlesPerTask);
            }

            // 更新 lastOpenTime 为当前批次最后 openTime
            stats.lastOpenTime = currentBatchLastOpenTime;

            // 推进游标
            cursor = currentBatchLastOpenTime + durationMs;
            if (progressCallback != null) {
                progressCallback.accept(stats);
            }

            log.debug("operation=rest-import-batch taskId={} batch={} fetched={} inserted={} existing={} conflict={} cursor={} lastOpenTime={}",
                    taskId, stats.batches, fetched.size(), batchResult.inserted, batchResult.existing,
                    batchResult.conflict, cursor, stats.lastOpenTime);
        }

        return stats;
    }

    // ---- 辅助方法 ----

    // ---- 内部类型 ----

    /** 导入统计。 */
    public static class ImportStats {
        public long fetched = 0;
        public long inserted = 0;
        public long existing = 0;
        public long conflict = 0;
        public int batches = 0;
        public long lastOpenTime = -1;
    }

    /** REST 导入异常，携带错误码。 */
    public static class RestImportException extends RuntimeException {
        public final String errorCode;

        public RestImportException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
