package com.aiprovider.config.quant;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(QuantBacktestProperties.class)
public class QuantBacktestConfiguration {
    @Bean public BacktestEngine backtestEngine() { return new BacktestEngine(); }
    @Bean public StrategyRegistry strategyRegistry() { return new StrategyRegistry(); }
    @Bean(name = "quantBacktestExecutor")
    public ThreadPoolTaskExecutor quantBacktestExecutor(QuantBacktestProperties p) {
        if (p.getExecutorMaxPoolSize() < p.getExecutorCorePoolSize()) throw new IllegalArgumentException("quant.backtest max pool must be >= core pool");
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(p.getExecutorCorePoolSize()); e.setMaxPoolSize(p.getExecutorMaxPoolSize());
        e.setQueueCapacity(p.getExecutorQueueCapacity()); e.setThreadNamePrefix("quant-backtest-");
        e.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        e.setWaitForTasksToCompleteOnShutdown(true); e.setAwaitTerminationSeconds(30); e.initialize();
        return e;
    }
}
