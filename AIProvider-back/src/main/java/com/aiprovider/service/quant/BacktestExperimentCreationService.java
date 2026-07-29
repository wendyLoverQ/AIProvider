package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for experiment and all candidate row creation. */
@Service
public class BacktestExperimentCreationService {
  private final BacktestExperimentService experiments;

  public BacktestExperimentCreationService(BacktestExperimentService experiments) {
    this.experiments = experiments;
  }

  @Transactional
  public BacktestExperimentDtos.CreateResponse create(BacktestExperimentCreateRequest request) {
  com.aiprovider.logging.BusinessOperationLogger.start("service.quant.BacktestExperimentCreationService.create", new String[] { "request" }, new Object[] { request });
  return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.BacktestExperimentCreationService.create", experiments.create(request));
  }

  @Transactional
  public BacktestExperimentDtos.CreateResponse createWithExperimentId(
      String experimentId, BacktestExperimentCreateRequest request) {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.BacktestExperimentCreationService.createWithExperimentId", new String[] { "experimentId", "request" }, new Object[] { experimentId, request });
      return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.BacktestExperimentCreationService.createWithExperimentId", experiments.createWithExperimentId(experimentId, request));
  }
}
