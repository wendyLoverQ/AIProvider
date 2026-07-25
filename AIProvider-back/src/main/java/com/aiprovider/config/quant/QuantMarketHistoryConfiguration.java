package com.aiprovider.config.quant;

import com.aiprovider.quant.market.history.port.HistoricalMarketDataProvider;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.port.MarketSyncTaskRepository;
import com.aiprovider.quant.market.history.port.SyncUnitOfWork;
import com.aiprovider.quant.market.history.service.MarketDatasetValidationService;
import com.aiprovider.quant.market.history.service.MarketHistorySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 历史行情同步 Spring 配置。
 *
 * 创建 {@link MarketHistorySyncService} Bean，注入配置参数（batchSize、maxCandlesPerTask）。
 * 创建有界执行器，单 worker + 有界队列，保证同一数据集不会并发同步。
 *
 * 执行器拒绝策略为 {@link ThreadPoolExecutor.CallerRunsPolicy}，
 * 队列满时在调用方线程执行，不丢弃任务也不抛异常。
 */
@Configuration
@EnableConfigurationProperties(QuantMarketHistoryProperties.class)
public class QuantMarketHistoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QuantMarketHistoryConfiguration.class);

    @Bean
    public MarketHistorySyncService marketHistorySyncService(
            HistoricalMarketDataProvider provider,
            MarketCandleRepository candleRepository,
            MarketDatasetRepository datasetRepository,
            MarketSyncTaskRepository taskRepository,
            MarketDatasetValidationService validationService,
            SyncUnitOfWork unitOfWork,
            QuantMarketHistoryProperties properties) {
        log.info("operation=market-history-init batchSize={} maxCandlesPerTask={}",
                properties.getBatchSize(), properties.getMaxCandlesPerTask());
        return new MarketHistorySyncService(
                provider,
                candleRepository,
                datasetRepository,
                taskRepository,
                validationService,
                unitOfWork,
                properties.getBatchSize(),
                properties.getMaxCandlesPerTask());
    }

    /**
     * 历史同步有界执行器。
     *
     * 单 worker（core=1, max=1）+ 有界队列，保证任务串行执行。
     * 队列满时在调用方线程执行，不拒绝也不丢弃任务。
     */
    @Bean(name = "quantHistorySyncExecutor")
    public ThreadPoolTaskExecutor quantHistorySyncExecutor(QuantMarketHistoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix(properties.getExecutorThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("operation=quant-history-executor-init corePoolSize={} maxPoolSize={} queueCapacity={}",
                properties.getExecutorCorePoolSize(), properties.getExecutorMaxPoolSize(),
                properties.getExecutorQueueCapacity());
        return executor;
    }
}
