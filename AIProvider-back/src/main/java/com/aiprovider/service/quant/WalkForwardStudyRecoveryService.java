package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantWalkForwardProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardStudyRecoveryService {
  private final WalkForwardStudyDispatcher dispatcher;
  private final QuantWalkForwardProperties properties;

  public WalkForwardStudyRecoveryService(
      WalkForwardStudyDispatcher dispatcher, QuantWalkForwardProperties properties) {
    this.dispatcher = dispatcher;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
  com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardStudyRecoveryService.recover", new String[] {}, new Object[] {});
  dispatcher.recoverStaleClaims(properties.getStaleClaimSeconds());
    dispatcher.tick();
    com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardStudyRecoveryService.recover", null);
  }
}
