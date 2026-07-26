package com.aiprovider.config.quant;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class QuantBacktestConfigurationTest {
    @Test void createsBoundedArrayBlockingQueueWithAbortPolicy(){QuantBacktestProperties p=new QuantBacktestProperties();ThreadPoolExecutor e=new QuantBacktestConfiguration().quantBacktestExecutor(p);assertTrue(e.getQueue() instanceof ArrayBlockingQueue);assertTrue(e.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);((QuantBacktestConfiguration.GracefulBacktestExecutor)e).shutdownGracefully();}
}
