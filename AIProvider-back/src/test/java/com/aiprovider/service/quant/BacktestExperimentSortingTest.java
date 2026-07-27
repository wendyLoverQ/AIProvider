package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExperimentSortingTest {
    @Test void mapsWhitelistSortToSafeSqlExpressionAndRejectsUnknownSort(){
        BacktestExperimentMapper experiments=mock(BacktestExperimentMapper.class);BacktestExperimentCandidateMapper candidates=mock(BacktestExperimentCandidateMapper.class);BacktestRunMapper runs=mock(BacktestRunMapper.class);BacktestExperimentRow experiment=new BacktestExperimentRow();experiment.experimentId="e";when(experiments.findByExperimentId("e")).thenReturn(experiment);when(candidates.findPageSorted(anyString(),anyInt(),anyInt(),anyString(),anyString())).thenReturn(List.of());
        BacktestExperimentService service=new BacktestExperimentService(experiments,candidates,runs,mock(com.aiprovider.quant.market.history.port.MarketDatasetRepository.class),new com.aiprovider.quant.strategy.StrategyRegistry(),new com.aiprovider.quant.execution.BacktestCompatibilityService(new com.aiprovider.quant.execution.ExecutionProfileRegistry()),new ObjectMapper(),new QuantExperimentProperties());
        service.candidates("e",1,50,"VALIDATION_TOTAL_RETURN_RATIO","DESC");verify(candidates).findPageSorted("e",50,0,"vr.TotalReturnRatio","DESC");
        assertThrows(BacktestTaskException.class,()->service.candidates("e",1,50,"DROP_TABLE","ASC"));
    }
}
