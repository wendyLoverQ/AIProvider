package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantResearchProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardOosRecoveryScheduler {
  private static final Logger log = LogManager.getLogger(WalkForwardOosRecoveryScheduler.class);
  private final WalkForwardOosRecoveryService recovery;
  private final QuantResearchProperties properties;

  public WalkForwardOosRecoveryScheduler(WalkForwardOosRecoveryService recovery, QuantResearchProperties properties) {
    this.recovery = recovery; this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverOnStartup() {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardOosRecoveryScheduler.recoverOnStartup", new String[] {}, new Object[] {});
      runOnce(); com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardOosRecoveryScheduler.recoverOnStartup", null);
}

  @Scheduled(fixedDelayString = "${quant.research.oos-recovery-interval-ms:5000}")
  public void scheduledRecovery() {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardOosRecoveryScheduler.scheduledRecovery", new String[] {}, new Object[] {});
      runOnce(); com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardOosRecoveryScheduler.scheduledRecovery", null);
}

  void runOnce() {
    try { recovery.recoverBatch(properties.getOosRecoveryBatchSize()); }
    catch (RuntimeException exception) { log.error("operation=walk-forward-oos-recovery result=retryable", exception); }
  }
}
