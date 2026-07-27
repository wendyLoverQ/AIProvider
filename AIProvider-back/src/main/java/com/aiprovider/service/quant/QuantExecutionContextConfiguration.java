package com.aiprovider.service.quant;

import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuantExecutionContextConfiguration {

  @Bean
  public ExecutionProfileRegistry executionProfileRegistry() {
    return new ExecutionProfileRegistry();
  }

  @Bean
  public BacktestCompatibilityService backtestCompatibilityService(
      ExecutionProfileRegistry profiles) {
    return new BacktestCompatibilityService(profiles);
  }
}
