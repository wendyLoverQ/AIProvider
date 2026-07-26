package com.aiprovider.config.quant;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.*;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({QuantBacktestProperties.class,QuantExperimentProperties.class})
public class QuantBacktestConfiguration {
    @Bean public StrategyRegistry strategyRegistry() { return new StrategyRegistry(); }
    @Bean public BacktestEngine backtestEngine(StrategyRegistry registry) { return new BacktestEngine(registry); }
    @Bean(name = "quantBacktestExecutor", destroyMethod = "shutdownGracefully")
    public ThreadPoolExecutor quantBacktestExecutor(QuantBacktestProperties p) {
        if (p.getExecutorCorePoolSize() < 1 || p.getExecutorCorePoolSize() > 8 || p.getExecutorMaxPoolSize() < 1 || p.getExecutorMaxPoolSize() > 16 || p.getExecutorQueueCapacity() < 1 || p.getExecutorQueueCapacity() > 1000 || p.getExecutorMaxPoolSize() < p.getExecutorCorePoolSize()) throw new IllegalArgumentException("quant.backtest executor limits invalid");
        return new GracefulBacktestExecutor(p.getExecutorCorePoolSize(),p.getExecutorMaxPoolSize(),p.getExecutorQueueCapacity());
    }
    static final class GracefulBacktestExecutor extends ThreadPoolExecutor {
        private static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger();
        GracefulBacktestExecutor(int core,int max,int capacity){super(core,max,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{Thread t=new Thread(r,"quant-backtest-"+IDS.incrementAndGet());t.setDaemon(false);return t;},new AbortPolicy());}
        public void shutdownGracefully(){shutdown();try{if(!awaitTermination(30,TimeUnit.SECONDS))shutdownNow();}catch(InterruptedException e){shutdownNow();Thread.currentThread().interrupt();}}
    }
}
