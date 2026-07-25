package com.aiprovider.config.quant;

import com.aiprovider.adapter.quant.BinancePublicDataArchiveAdapter;
import com.aiprovider.adapter.quant.MarketStorageStatePortImpl;
import com.aiprovider.mapper.MarketDatasetMapper;
import com.aiprovider.quant.market.history.port.HistoricalArchiveProvider;
import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDataGapRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketStorageStatePort;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import com.aiprovider.quant.market.history.service.ArchiveImportService;
import com.aiprovider.quant.market.history.service.ArchivePlanner;
import com.aiprovider.quant.market.history.service.MarketCandleIngestService;
import com.aiprovider.quant.market.history.service.MarketDatasetValidationService;
import com.aiprovider.quant.market.history.service.MarketHistoryQueryService;
import com.aiprovider.quant.market.history.service.MarketHistorySyncService;
import com.aiprovider.quant.market.history.service.MarketTaskGapCalculator;
import com.aiprovider.quant.market.history.service.RestKlineRangeImporter;
import com.aiprovider.quant.management.QuantOverviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 历史行情同步与归档导入 Spring 配置。
 *
 * 创建以下 Bean：
 * <ul>
 *   <li>{@link MarketCandleIngestService} — 统一 K 线写入管线，REST 与归档共用</li>
 *   <li>{@link ArchivePlanner} — Binance 官方 ZIP 归档下载计划规划器</li>
 *   <li>{@link HistoricalArchiveProvider} — Binance 公共数据归档适配器</li>
 *   <li>{@link MarketStorageStatePort} — 行情存储状态只读端口</li>
 *   <li>{@link MarketHistoryQueryService} — 历史行情查询服务</li>
 *   <li>{@link MarketDatasetValidationService} — 数据集校验服务</li>
 *   <li>{@link MarketHistorySyncService} — 历史行情同步服务</li>
 *   <li>{@link QuantOverviewService} — Quant 总览服务</li>
 *   <li>quantHistorySyncExecutor — 有界执行器</li>
 * </ul>
 *
 * 执行器拒绝策略为 {@link ThreadPoolExecutor.AbortPolicy}，
 * 队列满时抛出 {@link java.util.concurrent.RejectedExecutionException}，
 * 由应用服务捕获后将任务标记为 FAILED，不在 HTTP 请求线程执行同步。
 */
@Configuration
@EnableConfigurationProperties(QuantMarketHistoryProperties.class)
public class QuantMarketHistoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QuantMarketHistoryConfiguration.class);

    // ---- 写入管线 ----

    @Bean
    public MarketCandleIngestService marketCandleIngestService(
            MarketCandleRepository candleRepository,
            SyncUnitOfWork unitOfWork) {
        log.info("operation=ingest-service-init");
        return new MarketCandleIngestService(candleRepository, unitOfWork);
    }

    // ---- 共享组件（REST 同步与归档导入共用）----

    @Bean
    public MarketTaskGapCalculator marketTaskGapCalculator() {
        log.info("operation=gap-calculator-init");
        return new MarketTaskGapCalculator();
    }

    @Bean
    public RestKlineRangeImporter restKlineRangeImporter(
            HistoricalMarketDataProvider provider,
            MarketCandleIngestService ingestService,
            QuantMarketHistoryProperties properties) {
        log.info("operation=rest-importer-init batchSize={} maxCandlesPerTask={}",
                properties.getBatchSize(), properties.getMaxCandlesPerTask());
        return new RestKlineRangeImporter(provider, ingestService,
                properties.getBatchSize(), properties.getMaxCandlesPerTask());
    }

    // ---- 归档导入 ----

    @Bean
    public ArchivePlanner archivePlanner() {
        log.info("operation=archive-planner-init");
        return new ArchivePlanner();
    }

    @Bean
    public HistoricalArchiveProvider binancePublicDataArchiveAdapter(QuantMarketHistoryProperties properties) {
        log.info("operation=archive-adapter-init");
        return new BinancePublicDataArchiveAdapter(properties);
    }

    // ---- 存储状态 ----

    @Bean
    public MarketStorageStatePort marketStorageStatePort(MarketDatasetMapper datasetMapper) {
        log.info("operation=storage-state-port-init");
        return new MarketStorageStatePortImpl(datasetMapper);
    }

    // ---- 查询与校验 ----

    @Bean
    public MarketHistoryQueryService marketHistoryQueryService(
            MarketDatasetRepository datasetRepository,
            MarketCandleRepository candleRepository,
            MarketDataGapRepository gapRepository) {
        log.info("operation=history-query-service-init");
        return new MarketHistoryQueryService(datasetRepository, candleRepository, gapRepository);
    }

    @Bean
    public MarketDatasetValidationService marketDatasetValidationService(
            MarketCandleRepository candleRepository,
            MarketDatasetRepository datasetRepository,
            MarketDataGapRepository gapRepository,
            SyncUnitOfWork unitOfWork) {
        log.info("operation=dataset-validation-service-init");
        return new MarketDatasetValidationService(candleRepository, datasetRepository, gapRepository, unitOfWork);
    }

    // ---- 同步服务 ----

    @Bean
    public ArchiveImportService archiveImportService(
            ArchivePlanner planner,
            HistoricalArchiveProvider archiveProvider,
            MarketCandleIngestService ingestService,
            RestKlineRangeImporter restImporter,
            HistoricalMarketDataProvider marketDataProvider,
            MarketDatasetRepository datasetRepository,
            MarketSyncTaskRepository taskRepository,
            MarketDatasetValidationService validationService,
            MarketTaskGapCalculator gapCalculator) {
        log.info("operation=archive-import-service-init");
        return new ArchiveImportService(planner, archiveProvider, ingestService,
                restImporter, marketDataProvider,
                datasetRepository, taskRepository, validationService, gapCalculator);
    }

    @Bean
    public MarketHistorySyncService marketHistorySyncService(
            HistoricalMarketDataProvider provider,
            RestKlineRangeImporter restImporter,
            MarketTaskGapCalculator gapCalculator,
            MarketDatasetRepository datasetRepository,
            MarketSyncTaskRepository taskRepository,
            MarketDatasetValidationService validationService,
            QuantMarketHistoryProperties properties) {
        log.info("operation=market-history-init maxCandlesPerTask={}",
                properties.getMaxCandlesPerTask());
        return new MarketHistorySyncService(
                provider,
                restImporter,
                gapCalculator,
                datasetRepository,
                taskRepository,
                validationService,
                properties.getMaxCandlesPerTask());
    }

    /**
     * 历史同步有界执行器。
     *
     * 单 worker（core=1, max=1）+ 有界队列，保证任务串行执行。
     * 队列满时抛出 RejectedExecutionException，由应用服务捕获后标记任务 FAILED。
     */
    @Bean(name = "quantHistorySyncExecutor")
    public ThreadPoolTaskExecutor quantHistorySyncExecutor(QuantMarketHistoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix(properties.getExecutorThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("operation=quant-history-executor-init corePoolSize={} maxPoolSize={} queueCapacity={}",
                properties.getExecutorCorePoolSize(), properties.getExecutorMaxPoolSize(),
                properties.getExecutorQueueCapacity());
        return executor;
    }

    // ---- 总览 ----

    @Bean
    public QuantOverviewService quantOverviewService(MarketStorageStatePort storageStatePort) {
        log.info("operation=quant-overview-service-init");
        return new QuantOverviewService(storageStatePort);
    }
}
