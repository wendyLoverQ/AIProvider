package com.aiprovider.quant.market.history.service;

import com.aiprovider.quant.exchange.binance.usdm.BinanceUsdmUpstreamException;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketCandle;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史行情同步服务。
 *
 * 从 Binance 分页下载历史闭合 K 线，校验后批量写入数据库。
 * 每批操作在独立事务内完成，中途失败时已提交的 K 线保留。
 *
 * 核心设计：
 * <ul>
 *   <li>分页游标严格使用最后一根已验证 K 线的 openTime + duration 推进</li>
 *   <li>每页校验 openTime 升序、范围、对齐、OHLC 合法性</li>
 *   <li>冲突时不自动覆盖，任务失败并记录错误码</li>
 *   <li>Binance 429/418 不自动重试，任务失败并保存 Retry-After</li>
 *   <li>空页结束下载，进入缺口校验</li>
 * </ul>
 */
@Service
public class MarketHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistorySyncService.class);
    private static final String SOURCE = "BINANCE_USDM_REST";
    private static final String ERR_CURSOR_STALL = "CURSOR_NOT_ADVANCING";
    private static final String ERR_DUPLICATE_OPEN_TIME = "DUPLICATE_OPEN_TIME";
    private static final String ERR_OUT_OF_RANGE = "OUT_OF_RANGE";
    private static final String ERR_NOT_CLOSED = "NOT_CLOSED_KLINE";
    private static final String ERR_OHLC_INVALID = "OHLC_INVALID";
    private static final String ERR_VOLUME_NEGATIVE = "VOLUME_NEGATIVE";
    private static final String ERR_NOT_ALIGNED = "OPEN_TIME_NOT_ALIGNED";
    private static final String ERR_SYMBOL_MISMATCH = "SYMBOL_MISMATCH";
    private static final String ERR_INSERT_COUNT = "INSERT_COUNT_MISMATCH";
    private static final String ERR_CANDLE_CONFLICT = "CANDLE_DATA_CONFLICT";
    private static final String ERR_UPSTREAM = "UPSTREAM_ERROR";
    private static final String ERR_UNKNOWN = "UNKNOWN_ERROR";

    private final HistoricalMarketDataProvider provider;
    private final MarketCandleRepository candleRepository;
    private final MarketDatasetRepository datasetRepository;
    private final MarketSyncTaskRepository taskRepository;
    private final MarketDatasetValidationService validationService;
    private final SyncUnitOfWork unitOfWork;

    private final int batchSize;
    private final int maxCandlesPerTask;

    public MarketHistorySyncService(HistoricalMarketDataProvider provider,
                                     MarketCandleRepository candleRepository,
                                     MarketDatasetRepository datasetRepository,
                                     MarketSyncTaskRepository taskRepository,
                                     MarketDatasetValidationService validationService,
                                     SyncUnitOfWork unitOfWork,
                                     int batchSize, int maxCandlesPerTask) {
        this.provider = provider;
        this.candleRepository = candleRepository;
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
        this.validationService = validationService;
        this.unitOfWork = unitOfWork;
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

                // 校验本批数据
                validateBatch(fetched, cursor, normalizedEnd, serverTimeMs, interval, task.getSymbol(), lastOpenTime);

                // 更新游标为最后一根 K 线的 openTime
                long batchLastOpenTime = fetched.get(fetched.size() - 1).getOpenTime().toEpochMilli();
                if (batchLastOpenTime <= lastOpenTime) {
                    throw new SyncException(ERR_CURSOR_STALL,
                            "分页游标未推进: lastOpenTime=" + batchLastOpenTime + " cursor=" + cursor);
                }
                lastOpenTime = batchLastOpenTime;

                // 在事务内执行：查询已有 → 校验冲突 → 批量写入 → 更新进度
                BatchResult batchResult = unitOfWork.execute(() -> {
                    List<HistoricalCandle> toInsert = new ArrayList<>();
                    int batchExisting = 0;
                    int batchConflict = 0;

                    // 查询已存在的 K 线
                    List<Long> openTimes = new ArrayList<>();
                    for (MarketCandle c : fetched) {
                        openTimes.add(c.getOpenTime().toEpochMilli());
                    }
                    List<HistoricalCandle> existing = candleRepository.findByOpenTimes(task.getDatasetId(), openTimes);
                    Map<Long, HistoricalCandle> existingMap = new HashMap<>();
                    for (HistoricalCandle hc : existing) {
                        existingMap.put(hc.getOpenTime().toEpochMilli(), hc);
                    }

                    for (MarketCandle c : fetched) {
                        long openTimeMs = c.getOpenTime().toEpochMilli();
                        HistoricalCandle existingCandle = existingMap.get(openTimeMs);
                        if (existingCandle != null) {
                            if (candleMatches(c, existingCandle)) {
                                batchExisting++;
                            } else {
                                batchConflict++;
                                throw new SyncException(ERR_CANDLE_CONFLICT,
                                        "K 线数据冲突: symbol=" + task.getSymbol() + " interval=" + interval.code()
                                                + " openTime=" + Instant.ofEpochMilli(openTimeMs));
                            }
                        } else {
                            toInsert.add(toHistoricalCandle(c, task.getDatasetId()));
                        }
                    }

                    // 批量插入新 K 线
                    if (!toInsert.isEmpty()) {
                        int inserted = candleRepository.insertBatch(toInsert);
                        if (inserted != toInsert.size()) {
                            throw new SyncException(ERR_INSERT_COUNT,
                                    "批量插入影响行数不匹配: expected=" + toInsert.size() + " actual=" + inserted);
                        }
                    }

                    return new BatchResult(toInsert.size(), batchExisting, batchConflict);
                });

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

            // 计算请求范围内的缺口（包括首尾边界）
            long taskGapCount = calculateTaskGaps(task.getDatasetId(), interval, taskId,
                    normalizedStart, normalizedEnd,
                    validationResult.earliestOpenTimeMs, validationResult.latestOpenTimeMs);

            // 标记完成
            taskRepository.markCompleted(taskId, fetchedCount, insertedCount, existingCount, taskGapCount);

            // 更新数据集最后同步信息
            datasetRepository.updateLastSync(task.getDatasetId(), taskId, Instant.now());

            log.info("operation=sync-complete taskId={} datasetId={} fetched={} inserted={} existing={} conflict={} gaps={} batches={}",
                    taskId, task.getDatasetId(), fetchedCount, insertedCount, existingCount, conflictCount, taskGapCount, batchCount);

        } catch (BinanceUsdmUpstreamException e) {
            String errorCode = ERR_UPSTREAM;
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

    // ---- 批次校验 ----

    private void validateBatch(List<MarketCandle> fetched, long cursor, long normalizedEnd,
                                 long serverTimeMs, KlineInterval interval, String symbol, long lastOpenTime) {
        if (fetched.isEmpty()) return;

        long durationMs = interval.durationMillis();
        long prevOpenTime = lastOpenTime;

        for (int i = 0; i < fetched.size(); i++) {
            MarketCandle c = fetched.get(i);
            long openTimeMs = c.getOpenTime().toEpochMilli();
            long closeTimeMs = c.getCloseTime().toEpochMilli();

            // symbol 校验
            if (!symbol.equals(c.getSymbol())) {
                throw new SyncException(ERR_SYMBOL_MISMATCH,
                        "symbol 不匹配: expected=" + symbol + " actual=" + c.getSymbol());
            }

            // 范围校验
            if (openTimeMs < cursor) {
                throw new SyncException(ERR_OUT_OF_RANGE,
                        "K 线 openTime 早于游标: openTime=" + openTimeMs + " cursor=" + cursor);
            }
            if (openTimeMs >= normalizedEnd) {
                throw new SyncException(ERR_OUT_OF_RANGE,
                        "K 线 openTime 超出结束边界: openTime=" + openTimeMs + " end=" + normalizedEnd);
            }

            // 对齐校验
            if (openTimeMs % durationMs != 0) {
                throw new SyncException(ERR_NOT_ALIGNED,
                        "K 线 openTime 未对齐到周期: openTime=" + openTimeMs + " duration=" + durationMs);
            }

            // 升序校验
            if (i > 0 && openTimeMs <= prevOpenTime) {
                throw new SyncException(ERR_DUPLICATE_OPEN_TIME,
                        "同页 K 线 openTime 非升序或重复: prev=" + prevOpenTime + " current=" + openTimeMs);
            }
            if (prevOpenTime >= 0 && openTimeMs <= prevOpenTime) {
                throw new SyncException(ERR_DUPLICATE_OPEN_TIME,
                        "K 线 openTime 未超过前一根: prev=" + prevOpenTime + " current=" + openTimeMs);
            }

            // 闭合校验
            if (closeTimeMs >= serverTimeMs) {
                throw new SyncException(ERR_NOT_CLOSED,
                        "K 线未闭合: closeTime=" + closeTimeMs + " serverTime=" + serverTimeMs);
            }

            // OHLC 合法性
            BigDecimal high = c.getHigh();
            BigDecimal low = c.getLow();
            BigDecimal open = c.getOpen();
            BigDecimal close = c.getClose();
            if (high.compareTo(open) < 0 || high.compareTo(close) < 0) {
                throw new SyncException(ERR_OHLC_INVALID,
                        "high 小于 open 或 close: open=" + open + " high=" + high + " close=" + close);
            }
            if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
                throw new SyncException(ERR_OHLC_INVALID,
                        "low 大于 open 或 close: open=" + open + " low=" + low + " close=" + close);
            }

            // volume 非负
            if (c.getVolume().signum() < 0) {
                throw new SyncException(ERR_VOLUME_NEGATIVE,
                        "volume 为负数: " + c.getVolume());
            }

            prevOpenTime = openTimeMs;
        }
    }

    // ---- 冲突比较 ----

    private boolean candleMatches(MarketCandle fetched, HistoricalCandle existing) {
        return eq(fetched.getOpen(), existing.getOpenPrice())
                && eq(fetched.getHigh(), existing.getHighPrice())
                && eq(fetched.getLow(), existing.getLowPrice())
                && eq(fetched.getClose(), existing.getClosePrice())
                && eq(fetched.getVolume(), existing.getVolume())
                && eq(fetched.getQuoteVolume(), existing.getQuoteVolume())
                && fetched.getTradeCount() == existing.getTradeCount()
                && eq(fetched.getTakerBuyBaseVolume(), existing.getTakerBuyBaseVolume())
                && eq(fetched.getTakerBuyQuoteVolume(), existing.getTakerBuyQuoteVolume());
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    // ---- 转换 ----

    private HistoricalCandle toHistoricalCandle(MarketCandle candle, long datasetId) {
        HistoricalCandle hc = new HistoricalCandle();
        hc.setDatasetId(datasetId);
        hc.setProvider(candle.getProvider());
        hc.setMarketType(candle.getMarketType());
        hc.setSymbol(candle.getSymbol());
        hc.setInterval(candle.getInterval());
        hc.setOpenTime(candle.getOpenTime());
        hc.setCloseTime(candle.getCloseTime());
        hc.setOpenPrice(candle.getOpen());
        hc.setHighPrice(candle.getHigh());
        hc.setLowPrice(candle.getLow());
        hc.setClosePrice(candle.getClose());
        hc.setVolume(candle.getVolume());
        hc.setQuoteVolume(candle.getQuoteVolume());
        hc.setTradeCount(candle.getTradeCount());
        hc.setTakerBuyBaseVolume(candle.getTakerBuyBaseVolume());
        hc.setTakerBuyQuoteVolume(candle.getTakerBuyQuoteVolume());
        hc.setSource(SOURCE);
        return hc;
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

    private long calculateTaskGaps(long datasetId, KlineInterval interval, String taskId,
                                    long normalizedStart, long normalizedEnd,
                                    Long earliestMs, Long latestMs) {
        if (earliestMs == null || latestMs == null) {
            // 没有数据，整个请求范围都是缺口
            long durationMs = interval.durationMillis();
            return (normalizedEnd - normalizedStart) / durationMs;
        }
        long durationMs = interval.durationMillis();
        long gaps = 0;
        // 请求范围前部缺口
        if (earliestMs > normalizedStart) {
            gaps += (earliestMs - normalizedStart) / durationMs;
        }
        // 请求范围后部缺口
        if (latestMs + durationMs < normalizedEnd) {
            gaps += (normalizedEnd - (latestMs + durationMs)) / durationMs;
        }
        return gaps;
    }

    private static String truncateMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    // ---- 内部类型 ----

    private static class BatchResult {
        final int inserted;
        final int existing;
        final int conflict;
        BatchResult(int inserted, int existing, int conflict) {
            this.inserted = inserted;
            this.existing = existing;
            this.conflict = conflict;
        }
    }

    private static class SyncException extends RuntimeException {
        final String errorCode;
        SyncException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
