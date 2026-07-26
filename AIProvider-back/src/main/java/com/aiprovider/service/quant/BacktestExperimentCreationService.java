package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for experiment and all candidate row creation. */
@Service
public class BacktestExperimentCreationService {
    private final BacktestExperimentService experiments;
    public BacktestExperimentCreationService(BacktestExperimentService experiments){this.experiments=experiments;}
    @Transactional
    public BacktestExperimentDtos.CreateResponse create(BacktestExperimentCreateRequest request){return experiments.create(request);}
}
