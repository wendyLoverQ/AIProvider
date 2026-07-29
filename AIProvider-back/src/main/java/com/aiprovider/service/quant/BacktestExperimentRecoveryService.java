package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BacktestExperimentRecoveryService {
  private final BacktestExperimentCandidateMapper candidates;
  private final BacktestExperimentDispatcher dispatcher;
  private final QuantExperimentProperties properties;

  public BacktestExperimentRecoveryService(
      BacktestExperimentCandidateMapper candidates,
      BacktestExperimentDispatcher dispatcher,
      QuantExperimentProperties properties) {
    this.candidates = candidates;
    this.dispatcher = dispatcher;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
  com.aiprovider.logging.BusinessOperationLogger.start("service.quant.BacktestExperimentRecoveryService.recover", new String[] {}, new Object[] {});
  Instant now = Instant.now();
    candidates.resetStaleClaims(now.minusSeconds(properties.getStaleClaimSeconds()), now);
    dispatcher.tick();
    com.aiprovider.logging.BusinessOperationLogger.success("service.quant.BacktestExperimentRecoveryService.recover", null);
  }
}
