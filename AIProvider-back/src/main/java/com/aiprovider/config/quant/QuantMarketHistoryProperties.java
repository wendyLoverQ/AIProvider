package com.aiprovider.config.quant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 历史行情同步配置属性。
 *
 * 控制分页下载的批次大小、单任务最大 K 线数量，以及同步执行器的并发控制。
 */
@ConfigurationProperties(prefix = "quant.market-history")
public class QuantMarketHistoryProperties {

    /** 每次从 Binance 拉取的 K 线数量上限（1～1500）。 */
    private int batchSize = 500;

    /** 单个同步任务允许的最大 K 线数量，超过则拒绝。 */
    private int maxCandlesPerTask = 100_000;

    /** 同步执行器核心线程数（单 worker 保证任务串行执行）。 */
    private int executorCorePoolSize = 1;

    /** 同步执行器最大线程数。 */
    private int executorMaxPoolSize = 1;

    /** 同步执行器队列容量，超出则拒绝新任务。 */
    private int executorQueueCapacity = 16;

    /** 同步执行器线程名前缀。 */
    private String executorThreadNamePrefix = "quant-history-sync-";

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getMaxCandlesPerTask() { return maxCandlesPerTask; }
    public void setMaxCandlesPerTask(int maxCandlesPerTask) { this.maxCandlesPerTask = maxCandlesPerTask; }

    public int getExecutorCorePoolSize() { return executorCorePoolSize; }
    public void setExecutorCorePoolSize(int executorCorePoolSize) { this.executorCorePoolSize = executorCorePoolSize; }

    public int getExecutorMaxPoolSize() { return executorMaxPoolSize; }
    public void setExecutorMaxPoolSize(int executorMaxPoolSize) { this.executorMaxPoolSize = executorMaxPoolSize; }

    public int getExecutorQueueCapacity() { return executorQueueCapacity; }
    public void setExecutorQueueCapacity(int executorQueueCapacity) { this.executorQueueCapacity = executorQueueCapacity; }

    public String getExecutorThreadNamePrefix() { return executorThreadNamePrefix; }
    public void setExecutorThreadNamePrefix(String executorThreadNamePrefix) {
        this.executorThreadNamePrefix = executorThreadNamePrefix;
    }
}
