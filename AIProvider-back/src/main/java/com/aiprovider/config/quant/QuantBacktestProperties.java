package com.aiprovider.config.quant;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "quant.backtest")
@Validated
public class QuantBacktestProperties {
    @Min(1) private int executorCorePoolSize = 1;
    @Min(1) private int executorMaxPoolSize = 1;
    @Min(1) private int executorQueueCapacity = 10;
    @Min(100) @Max(5000) private int tradeInsertBatchSize = 500;
    @Min(100) @Max(5000) private int equityInsertBatchSize = 1000;
    public int getExecutorCorePoolSize() { return executorCorePoolSize; }
    public void setExecutorCorePoolSize(int v) { executorCorePoolSize = v; }
    public int getExecutorMaxPoolSize() { return executorMaxPoolSize; }
    public void setExecutorMaxPoolSize(int v) { executorMaxPoolSize = v; }
    public int getExecutorQueueCapacity() { return executorQueueCapacity; }
    public void setExecutorQueueCapacity(int v) { executorQueueCapacity = v; }
    public int getTradeInsertBatchSize() { return tradeInsertBatchSize; }
    public void setTradeInsertBatchSize(int v) { tradeInsertBatchSize = v; }
    public int getEquityInsertBatchSize() { return equityInsertBatchSize; }
    public void setEquityInsertBatchSize(int v) { equityInsertBatchSize = v; }
}
