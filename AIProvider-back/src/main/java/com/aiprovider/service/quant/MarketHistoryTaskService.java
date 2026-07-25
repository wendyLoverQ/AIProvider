package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantMarketHistoryProperties;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.service.ArchiveImportService;
import com.aiprovider.quant.market.history.service.MarketHistorySyncService;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

/**
 * 历史行情同步任务应用服务。
 *
 * 负责任务的创建、校验、提交和生命周期管理：
 * <ul>
 *   <li>校验请求参数（周期、符号、时间范围、K 线数量上限）</li>
 *   <li>归一化时间范围到周期边界</li>
 *   <li>查找或创建数据集</li>
 *   <li>插入任务记录，通过 DB 唯一约束（ActiveDatasetKey）防止同一数据集并发同步</li>
 *   <li>提交到有界执行器，队列满时标记任务失败</li>
 *   <li>应用启动时恢复中断任务</li>
 * </ul>
 *
 * 同步执行由 {@link MarketHistorySyncService} 在执行器线程中完成，
 * 不在 HTTP 请求线程中执行。
 */
@Service
public class MarketHistoryTaskService {

    private static final Logger log = LoggerFactory.getLogger(MarketHistoryTaskService.class);
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{1,32}$");
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final MarketDataType DATA_TYPE = MarketDataType.CANDLE;

    private final MarketHistorySyncService syncService;
    private final ArchiveImportService archiveImportService;
    private final MarketSyncTaskRepository taskRepository;
    private final MarketDatasetRepository datasetRepository;
    private final QuantMarketHistoryProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public MarketHistoryTaskService(MarketHistorySyncService syncService,
                                     ArchiveImportService archiveImportService,
                                     MarketSyncTaskRepository taskRepository,
                                     MarketDatasetRepository datasetRepository,
                                     QuantMarketHistoryProperties properties,
                                     @Qualifier("quantHistorySyncExecutor") ThreadPoolTaskExecutor executor) {
        this.syncService = syncService;
        this.archiveImportService = archiveImportService;
        this.taskRepository = taskRepository;
        this.datasetRepository = datasetRepository;
        this.properties = properties;
        this.executor = executor;
    }

    // ---- 任务创建 ----

    /**
     * 创建历史行情同步任务（REST 数据源）。
     *
     * @param symbol       合约符号（如 BTCUSDT）
     * @param intervalCode K 线周期代码（如 1m、5m、15m、1h、4h、1d）
     * @param startTime    请求起始时间
     * @param endTime      请求结束时间
     * @return 任务 ID（UUID）
     * @throws MarketHistoryTaskException 参数校验失败或数据集正在同步
     */
    public String createSyncTask(String symbol, String intervalCode,
                                  Instant startTime, Instant endTime) {
        MarketSyncTask task = prepareTask(symbol, intervalCode, startTime, endTime);
        return submitTask(task, () -> syncService.executeSync(task), "sync");
    }

    /**
     * 创建历史行情归档导入任务（Binance 官方 ZIP 数据源）。
     *
     * 使用 Binance 官方 data.binance.vision 的月包和日包下载历史 K 线，
     * 适用于大范围历史数据回填。归档截止（昨天 00:00 UTC）之后的数据
     * 需要另行创建 REST 同步任务修补。
     *
     * @param symbol       合约符号（如 BTCUSDT）
     * @param intervalCode K 线周期代码
     * @param startTime    请求起始时间
     * @param endTime      请求结束时间
     * @return 任务 ID（UUID）
     * @throws MarketHistoryTaskException 参数校验失败或数据集正在同步
     */
    public String createArchiveImportTask(String symbol, String intervalCode,
                                           Instant startTime, Instant endTime) {
        MarketSyncTask task = prepareTask(symbol, intervalCode, startTime, endTime);
        return submitTask(task, () -> archiveImportService.executeArchiveImport(task), "archive-import");
    }

    // ---- 任务准备 ----

    /**
     * 校验参数、归一化时间范围、查找或创建数据集，构建待插入任务对象。
     * REST 同步和归档导入共用此方法。
     */
    private MarketSyncTask prepareTask(String symbol, String intervalCode,
                                        Instant startTime, Instant endTime) {
        // 1. 校验周期
        KlineInterval interval = KlineInterval.fromCode(intervalCode);
        if (!interval.isSyncSupported()) {
            throw new MarketHistoryTaskException("INTERVAL_NOT_SUPPORTED",
                    "不支持的同步周期: " + intervalCode + "，仅支持 " + KlineInterval.SYNC_SUPPORTED);
        }

        // 2. 校验符号
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (!SYMBOL_PATTERN.matcher(sym).matches()) {
            throw new MarketHistoryTaskException("INVALID_SYMBOL",
                    "合约符号格式不正确，应为大写英数字: " + symbol);
        }

        // 3. 校验时间范围
        if (startTime == null || endTime == null) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "起止时间不能为空");
        }
        if (!startTime.isBefore(endTime)) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "起始时间必须早于结束时间");
        }

        // 4. 归一化到周期边界
        long durationMs = interval.durationMillis();
        long normalizedStartMs = interval.alignOpenTime(startTime).toEpochMilli();
        long normalizedEndMs = interval.alignOpenTime(endTime).toEpochMilli();

        // 5. 钳制结束时间到当前周期（排除未闭合 K 线）
        long nowAlignedMs = interval.alignOpenTime(Instant.now()).toEpochMilli();
        if (normalizedEndMs > nowAlignedMs) {
            normalizedEndMs = nowAlignedMs;
        }

        // 6. 校验归一化后范围
        if (normalizedEndMs <= normalizedStartMs) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "归一化后时间范围为空或无效");
        }

        // 7. 计算预期数量并校验上限
        long expectedCount = (normalizedEndMs - normalizedStartMs) / durationMs;
        if (expectedCount <= 0) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "预期 K 线数量为 0");
        }
        if (expectedCount > properties.getMaxCandlesPerTask()) {
            throw new MarketHistoryTaskException("MAX_CANDLES_EXCEEDED",
                    "预期 K 线数量超过上限: expected=" + expectedCount
                            + " max=" + properties.getMaxCandlesPerTask());
        }

        // 8. 查找或创建数据集
        MarketDataset dataset = findOrCreateDataset(sym, interval);

        // 9. 构建任务
        String taskId = UUID.randomUUID().toString();
        String activeKey = dataset.activeDatasetKey();

        MarketSyncTask task = new MarketSyncTask();
        task.setTaskId(taskId);
        task.setDatasetId(dataset.getId());
        task.setActiveDatasetKey(activeKey);
        task.setProvider(PROVIDER);
        task.setMarketType(MARKET_TYPE);
        task.setDataType(DATA_TYPE);
        task.setSymbol(sym);
        task.setInterval(interval);
        task.setRequestedStartTime(startTime);
        task.setRequestedEndTime(endTime);
        task.setNormalizedStartTime(Instant.ofEpochMilli(normalizedStartMs));
        task.setNormalizedEndTime(Instant.ofEpochMilli(normalizedEndMs));
        task.setExpectedCount(expectedCount);
        task.setStatus(MarketSyncTaskStatus.QUEUED);

        return task;
    }

    /**
     * 插入任务记录并提交到执行器。
     * REST 同步和归档导入共用此方法，差异仅在 execution 参数。
     */
    private String submitTask(MarketSyncTask task, Runnable execution, String operationName) {
        String taskId = task.getTaskId();
        String activeKey = task.getActiveDatasetKey();

        // 10. 插入任务（DB 唯一约束 ActiveDatasetKey 防止并发同步）
        try {
            taskRepository.insert(task);
        } catch (DuplicateKeyException e) {
            throw new MarketHistoryTaskException("DATASET_ALREADY_SYNCING",
                    "该数据集已有同步任务正在执行: " + activeKey);
        }

        // 11. 提交到执行器
        try {
            executor.execute(execution);
        } catch (RejectedExecutionException e) {
            taskRepository.markFailed(taskId, "EXECUTOR_QUEUE_FULL",
                    "同步队列已满，请稍后重试", null, null);
            taskRepository.clearActiveLock(taskId);
            log.warn("operation=create-{}-task-rejected taskId={} activeKey={} msg=执行器队列已满",
                    operationName, taskId, activeKey);
            throw new MarketHistoryTaskException("EXECUTOR_QUEUE_FULL",
                    "同步队列已满，请稍后重试");
        }

        log.info("operation=create-{}-task taskId={} datasetId={} symbol={} interval={} normalizedStart={} normalizedEnd={} expectedCount={}",
                operationName, taskId, task.getDatasetId(), task.getSymbol(), task.getInterval().code(),
                task.getNormalizedStartTime().toEpochMilli(), task.getNormalizedEndTime().toEpochMilli(),
                task.getExpectedCount());

        return taskId;
    }

    // ---- 启动恢复 ----

    /**
     * 应用启动时恢复中断的任务。
     *
     * 将所有非终态任务（QUEUED/DOWNLOADING/WRITING/VALIDATING）
     * 标记为 FAILED，错误码 SERVICE_RESTART_INTERRUPTED，
     * 并清除 ActiveDatasetKey 锁。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        int interrupted = taskRepository.markNonTerminalAsInterrupted();
        if (interrupted > 0) {
            log.warn("operation=recover-interrupted-tasks count={}", interrupted);
        } else {
            log.info("operation=recover-interrupted-tasks count=0");
        }
    }

    // ---- 查询 ----

    /**
     * 根据任务 ID 查询任务。
     */
    public MarketSyncTask getTask(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 分页查询任务列表，按排队时间倒序。
     */
    public List<MarketSyncTask> listTasks(int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        return taskRepository.findPage(pageSize, offset);
    }

    /**
     * 查询非终态任务列表（用于前端轮询）。
     */
    public List<MarketSyncTask> listNonTerminalTasks() {
        return taskRepository.findNonTerminal();
    }

    // ---- 内部方法 ----

    /**
     * 查找或创建数据集。
     *
     * 并发创建时，DB 唯一约束 uk_dataset_key 会阻止重复插入，
     * 捕获后重新查找即可获得已创建的数据集。
     */
    private MarketDataset findOrCreateDataset(String symbol, KlineInterval interval) {
        MarketDataset dataset = datasetRepository.findByKey(
                PROVIDER, MARKET_TYPE, DATA_TYPE.name(), symbol, interval.code());
        if (dataset != null) {
            return dataset;
        }

        dataset = new MarketDataset();
        dataset.setProvider(PROVIDER);
        dataset.setMarketType(MARKET_TYPE);
        dataset.setDataType(DATA_TYPE);
        dataset.setSymbol(symbol);
        dataset.setInterval(interval);
        dataset.setStatus(MarketDatasetStatus.EMPTY);
        dataset.setCandleCount(0);
        dataset.setGapCount(0);
        dataset.setExpectedInsideRange(0);

        try {
            long datasetId = datasetRepository.insert(dataset);
            dataset.setId(datasetId);
            log.info("operation=create-dataset datasetId={} symbol={} interval={}",
                    datasetId, symbol, interval.code());
            return dataset;
        } catch (DuplicateKeyException e) {
            // 并发创建，重新查找
            dataset = datasetRepository.findByKey(
                    PROVIDER, MARKET_TYPE, DATA_TYPE.name(), symbol, interval.code());
            if (dataset == null) {
                throw new MarketHistoryTaskException("DATASET_CREATE_FAILED",
                        "数据集创建失败且重新查找未找到: " + symbol + " " + interval.code());
            }
            return dataset;
        }
    }
}
