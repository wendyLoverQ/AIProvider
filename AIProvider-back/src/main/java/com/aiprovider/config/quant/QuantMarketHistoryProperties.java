package com.aiprovider.config.quant;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 历史行情同步配置属性。
 *
 * 控制分页下载的批次大小、单任务最大 K 线数量，以及同步执行器的并发控制。
 * 使用 {@link Validated} + Bean Validation 在应用启动时校验配置。
 *
 * 执行器核心/最大线程数锁定为 1，不允许通过环境变量调多为多个后破坏串行约束。
 */
@ConfigurationProperties(prefix = "quant.market-history")
@Validated
public class QuantMarketHistoryProperties {

    /** 每次从 Binance 拉取的 K 线数量上限（1～1500）。 */
    @Min(1)
    @Max(1500)
    private int batchSize = 500;

    /** 单个同步任务允许的最大 K 线数量，超过则拒绝。 */
    @Min(1)
    private int maxCandlesPerTask = 100_000;

    /** Maximum number of candles allowed in one immutable backtest snapshot. */
    @Min(1000)
    @Max(2_000_000)
    private int backtestSnapshotMaxCandles = 200_000;

    /** 同步执行器核心线程数（必须为 1，单 worker 保证任务串行执行）。 */
    @Min(1)
    @Max(1)
    private int executorCorePoolSize = 1;

    /** 同步执行器最大线程数（必须为 1）。 */
    @Min(1)
    @Max(1)
    private int executorMaxPoolSize = 1;

    /** 同步执行器队列容量，超出则拒绝新任务。 */
    @Min(1)
    private int executorQueueCapacity = 16;

    /** 同步执行器线程名前缀。 */
    @NotBlank
    private String executorThreadNamePrefix = "quant-history-sync-";

    /** Binance 官方归档下载配置。 */
    @Valid
    private Archive archive = new Archive();

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getMaxCandlesPerTask() { return maxCandlesPerTask; }
    public void setMaxCandlesPerTask(int maxCandlesPerTask) { this.maxCandlesPerTask = maxCandlesPerTask; }

    public int getBacktestSnapshotMaxCandles() { return backtestSnapshotMaxCandles; }
    public void setBacktestSnapshotMaxCandles(int backtestSnapshotMaxCandles) {
        this.backtestSnapshotMaxCandles = backtestSnapshotMaxCandles;
    }

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

    public Archive getArchive() { return archive; }
    public void setArchive(Archive archive) { this.archive = archive; }

    /**
     * Binance 官方归档下载配置。
     *
     * 控制 base URL、工作目录、超时、最大 ZIP 体积和 CSV 解析批大小。
     * 所有值可通过 application.yml / 环境变量覆盖。
     */
    public static class Archive {

        /** Binance 公共数据下载基址。 */
        @NotBlank
        private String baseUrl = "https://data.binance.vision/";

        /** 临时下载工作目录。 */
        @NotBlank
        private String workDir = System.getProperty("java.io.tmpdir") + "/aiprovider/quant-history";

        /** HTTP 连接建立超时（毫秒）。 */
        @Min(1000)
        private int connectTimeoutMs = 30_000;

        /** 单次 HTTP 请求超时（毫秒），覆盖 CHECKSUM 与 ZIP 下载。 */
        @Min(1000)
        private int requestTimeoutMs = 600_000;

        /** 单个 ZIP 文件最大字节数，超过立即中止下载。 */
        @Min(1)
        private long maxZipSizeBytes = 536_870_912L;

        /** CSV 解析批大小，每批回调一次 consumer。 */
        @Min(1)
        private int parseBatchSize = 1000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getWorkDir() { return workDir; }
        public void setWorkDir(String workDir) { this.workDir = workDir; }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }

        public long getMaxZipSizeBytes() { return maxZipSizeBytes; }
        public void setMaxZipSizeBytes(long maxZipSizeBytes) { this.maxZipSizeBytes = maxZipSizeBytes; }

        public int getParseBatchSize() { return parseBatchSize; }
        public void setParseBatchSize(int parseBatchSize) { this.parseBatchSize = parseBatchSize; }
    }
}
