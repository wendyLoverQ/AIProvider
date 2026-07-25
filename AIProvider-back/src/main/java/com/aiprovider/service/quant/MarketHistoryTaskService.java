package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantMarketHistoryProperties;
import com.aiprovider.quant.market.history.model.ArchiveImportMode;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.model.MarketDatasetStatus;
import com.aiprovider.quant.market.history.model.MarketSyncTask;
import com.aiprovider.quant.market.history.model.MarketSyncTaskStatus;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.service.ArchiveImportService;
import com.aiprovider.quant.market.history.service.MarketHistorySyncService;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import com.aiprovider.quant.market.service.PublicMarketQueryService;
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
    private final PublicMarketQueryService publicMarketQueryService;
    private final HistoricalMarketDataProvider historicalMarketDataProvider;

    public MarketHistoryTaskService(MarketHistorySyncService syncService,
                                     ArchiveImportService archiveImportService,
                                     MarketSyncTaskRepository taskRepository,
                                     MarketDatasetRepository datasetRepository,
                                     QuantMarketHistoryProperties properties,
                                     @Qualifier("quantHistorySyncExecutor") ThreadPoolTaskExecutor executor,
                                     PublicMarketQueryService publicMarketQueryService,
                                     HistoricalMarketDataProvider historicalMarketDataProvider) {
        this.syncService = syncService;
        this.archiveImportService = archiveImportService;
        this.taskRepository = taskRepository;
        this.datasetRepository = datasetRepository;
        this.properties = properties;
        this.executor = executor;
        this.publicMarketQueryService = publicMarketQueryService;
        this.historicalMarketDataProvider = historicalMarketDataProvider;
    }

    // ---- 任务创建 ----

    /**
     * 统一创建历史行情同步任务，按 sourceMode 路由到对应导入管线。
     *
     * <p>支持的数据来源模式：</p>
     * <ul>
     *   <li>{@code AUTO} — 自动选择月包、日包和 REST 尾部，单任务完成全范围回填</li>
     *   <li>{@code REST_GAP_REPAIR} — 只用 /fapi/v1/klines 修补指定范围</li>
     *   <li>{@code ARCHIVE_MONTHLY} — 只导入完整月包</li>
     *   <li>{@code ARCHIVE_DAILY} — 只导入指定日包</li>
     * </ul>
     *
     * <p>无论哪种模式，都会先校验真实合约（symbol/contractType/status/quoteAsset/
     * supportedIntervals），复用 {@link PublicMarketQueryService} 已拉取的合约目录，
     * 不重新请求第二套 exchangeInfo。上游目录请求失败时不会创建 dataset 或 task。</p>
     *
     * @param symbol       合约符号（如 BTCUSDT）
     * @param intervalCode K 线周期代码（如 1m、5m、15m、1h、4h、1d）
     * @param startTime    请求起始时间
     * @param endTime      请求结束时间
     * @param sourceMode   数据来源模式（AUTO/REST_GAP_REPAIR/ARCHIVE_MONTHLY/ARCHIVE_DAILY）
     * @return 任务 ID（UUID）
     * @throws MarketHistoryTaskException 参数校验失败、合约不存在或数据集正在同步
     */
    public String createTask(String provider, String marketType, String symbol, String intervalCode,
                              Instant startTime, Instant endTime, String sourceMode) {
        if (!MarketProviderId.BINANCE_USDM.name().equals(provider)) {
            throw new MarketHistoryTaskException("INVALID_PROVIDER", "不支持的行情提供方: " + provider);
        }
        if (!MarketType.USDM_PERPETUAL.name().equals(marketType)) {
            throw new MarketHistoryTaskException("INVALID_MARKET_TYPE", "不支持的市场类型: " + marketType);
        }
        // 校验并归一化 sourceMode（ArchiveImportMode 枚举保证取值合法）
        ArchiveImportMode mode = parseSourceMode(sourceMode);
        MarketSyncTask task = prepareTask(symbol, intervalCode, startTime, endTime, mode);
        Runnable execution = selectExecution(mode, task);
        return submitTask(task, execution, "sync-" + mode.name());
    }

    @Deprecated
    public String createTask(String symbol, String intervalCode, Instant startTime, Instant endTime, String sourceMode) {
        return createTask(MarketProviderId.BINANCE_USDM.name(), MarketType.USDM_PERPETUAL.name(),
                symbol, intervalCode, startTime, endTime, sourceMode);
    }

    private ArchiveImportMode parseSourceMode(String sourceMode) {
        if (sourceMode == null || sourceMode.isBlank()) {
            throw new MarketHistoryTaskException("INVALID_SOURCE_MODE",
                    "数据来源模式不能为空，支持: AUTO/REST_GAP_REPAIR/ARCHIVE_MONTHLY/ARCHIVE_DAILY");
        }
        try {
            return ArchiveImportMode.valueOf(sourceMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new MarketHistoryTaskException("INVALID_SOURCE_MODE",
                    "不支持的数据来源模式: " + sourceMode + "，支持: AUTO/REST_GAP_REPAIR/ARCHIVE_MONTHLY/ARCHIVE_DAILY");
        }
    }

    private Runnable selectExecution(ArchiveImportMode mode, MarketSyncTask task) {
        switch (mode) {
            case REST_GAP_REPAIR:
                return () -> syncService.executeSync(task);
            case AUTO:
            case ARCHIVE_MONTHLY:
            case ARCHIVE_DAILY:
                return () -> archiveImportService.executeArchiveImport(task);
            default:
                throw new MarketHistoryTaskException("INVALID_SOURCE_MODE",
                        "不支持的数据来源模式: " + mode);
        }
    }

    // ---- 任务准备 ----

    /**
     * 校验参数、归一化时间范围、查找或创建数据集，构建待插入任务对象。
     * REST 同步和归档导入共用此方法。
     *
     * <p>合约校验复用 {@link PublicMarketQueryService#contracts} 已拉取的合约目录，
     * 不重新请求第二套 exchangeInfo。校验失败抛 {@code CONTRACT_NOT_FOUND}（映射 404），
     * 此时不会创建 dataset 或 task。</p>
     */
    private MarketSyncTask prepareTask(String symbol, String intervalCode,
                                        Instant startTime, Instant endTime, ArchiveImportMode mode) {
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

        // 3. 动态合约校验：复用 PublicMarketQueryService 已拉取的合约目录
        validateContract(sym, interval);

        // 4. 校验时间范围
        if (startTime == null || endTime == null) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "起止时间不能为空");
        }
        if (!startTime.isBefore(endTime)) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "起始时间必须早于结束时间");
        }

        // 5. 归一化到周期边界
        long durationMs = interval.durationMillis();
        long normalizedStartMs = interval.alignOpenTime(startTime).toEpochMilli();
        long normalizedEndMs = interval.alignOpenTime(endTime).toEpochMilli();

        // 6. 钳制结束时间到当前周期（排除未闭合 K 线）
        long referenceServerTimeMs = historicalMarketDataProvider.serverTime().toEpochMilli();
        long nowAlignedMs = interval.alignOpenTime(Instant.ofEpochMilli(referenceServerTimeMs)).toEpochMilli();
        if (normalizedEndMs > nowAlignedMs) {
            normalizedEndMs = nowAlignedMs;
        }

        // 7. 校验归一化后范围
        if (normalizedEndMs <= normalizedStartMs) {
            throw new MarketHistoryTaskException("INVALID_TIME_RANGE",
                    "归一化后时间范围为空或无效");
        }

        // 8. 计算预期数量并校验上限
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

        // 9. 查找或创建数据集
        MarketDataset dataset = findOrCreateDataset(sym, interval);

        // 10. 构建任务
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
        task.setSourceMode(mode.name());

        return task;
    }

    // ---- 合约校验 ----

    /**
     * 动态合约校验。
     *
     * 复用 {@link PublicMarketQueryService#contracts} 已拉取的合约目录（来源于
     * /fapi/v1/exchangeInfo），不重新请求第二套 exchangeInfo。要求合约存在、
     * {@code status=TRADING}、{@code contractType=PERPETUAL}、且支持请求的 K 线周期。
     *
     * @param symbol   已校验的合约符号（大写）
     * @param interval 已校验的 K 线周期
     * @throws MarketHistoryTaskException 合约不存在或不支持时抛 {@code CONTRACT_NOT_FOUND}（映射 404）
     */
    private void validateContract(String symbol, KlineInterval interval) {
        List<PerpetualContract> contracts = publicMarketQueryService.contracts(PROVIDER, "USDT");
        boolean valid = contracts.stream().anyMatch(c ->
                symbol.equals(c.getSymbol())
                        && "TRADING".equals(c.getStatus())
                        && "PERPETUAL".equals(c.getContractType())
                        && c.getSupportedIntervals() != null
                        && c.getSupportedIntervals().contains(interval));
        if (!valid) {
            throw new MarketHistoryTaskException("CONTRACT_NOT_FOUND",
                    "合约不存在、非 TRADING、非 PERPETUAL 或不支持该周期: symbol=" + symbol
                            + " interval=" + interval.code());
        }
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
