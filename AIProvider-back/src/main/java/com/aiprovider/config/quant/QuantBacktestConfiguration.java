package com.aiprovider.config.quant;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;

@Configuration
@EnableConfigurationProperties(QuantBacktestProperties.class)
public class QuantBacktestConfiguration {
    @Bean public BacktestEngine backtestEngine() { return new BacktestEngine(); }
    @Bean public StrategyRegistry strategyRegistry() { return new StrategyRegistry(); }
    @Bean(name = "quantBacktestExecutor", destroyMethod = "shutdownGracefully")
    public ThreadPoolExecutor quantBacktestExecutor(QuantBacktestProperties p) {
        if (p.getExecutorMaxPoolSize() < p.getExecutorCorePoolSize()) throw new IllegalArgumentException("quant.backtest max pool must be >= core pool");
        return new GracefulBacktestExecutor(p.getExecutorCorePoolSize(),p.getExecutorMaxPoolSize(),p.getExecutorQueueCapacity());
    }
    static final class GracefulBacktestExecutor extends ThreadPoolExecutor {
        GracefulBacktestExecutor(int core,int max,int capacity){super(core,max,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{Thread t=new Thread(r,"quant-backtest-");t.setDaemon(false);return t;},new AbortPolicy());}
        public void shutdownGracefully(){shutdown();try{if(!awaitTermination(30,TimeUnit.SECONDS))shutdownNow();}catch(InterruptedException e){shutdownNow();Thread.currentThread().interrupt();}}
    }
}
