package com.aiprovider.service.quant;

import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.aiprovider.config.quant.QuantExperimentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExperimentDispatcherTest {
    @Test void doesNotClaimWhenExperimentCapacityIsFull(){
        BacktestExperimentMapper experiments=mock(BacktestExperimentMapper.class);BacktestExperimentCandidateMapper candidates=mock(BacktestExperimentCandidateMapper.class);BacktestExperimentService aggregate=mock(BacktestExperimentService.class);
        when(experiments.findNonTerminal()).thenReturn(List.of(row()));when(candidates.countActive(anyString())).thenReturn(2);
        new BacktestExperimentDispatcher(experiments,candidates,mock(BacktestRunMapper.class),mock(BacktestRunService.class),aggregate,properties(2),new ObjectMapper()).tick();
        verify(candidates,never()).claimNextPending(anyString(),anyString(),any());
    }
    @Test void usesFixedIdsAndMarksCandidateOnlyAfterBothRunsAreCreated(){
        BacktestExperimentMapper experiments=mock(BacktestExperimentMapper.class);BacktestExperimentCandidateMapper candidates=mock(BacktestExperimentCandidateMapper.class);BacktestRunService runs=mock(BacktestRunService.class);BacktestExperimentService aggregate=mock(BacktestExperimentService.class);BacktestExperimentRow experiment=row();BacktestExperimentCandidateRow candidate=new BacktestExperimentCandidateRow();candidate.candidateId="c";candidate.experimentId=experiment.experimentId;candidate.parametersJson="{\"fastPeriod\":5,\"slowPeriod\":20}";candidate.trainingRunId="t";candidate.validationRunId="v";
        when(experiments.findNonTerminal()).thenReturn(List.of(experiment));when(candidates.countActive(anyString())).thenReturn(0);when(candidates.claimNextPending(anyString(),anyString(),any())).thenReturn(1);when(candidates.findClaimed(anyString(),anyString())).thenReturn(candidate);when(candidates.markDispatched(anyString(),anyString(),any())).thenReturn(1);
        new BacktestExperimentDispatcher(experiments,candidates,mock(BacktestRunMapper.class),runs,aggregate,properties(1),new ObjectMapper()).tick();
        verify(runs).createWithRunId(eq("t"),any());verify(runs).createWithRunId(eq("v"),any());verify(candidates).markDispatched(eq("c"),anyString(),any());
    }
    @Test void aConcurrentCasMissStopsTheTick(){
        BacktestExperimentMapper experiments=mock(BacktestExperimentMapper.class);BacktestExperimentCandidateMapper candidates=mock(BacktestExperimentCandidateMapper.class);when(experiments.findNonTerminal()).thenReturn(List.of(row()));when(candidates.countActive(anyString())).thenReturn(0);when(candidates.claimNextPending(anyString(),anyString(),any())).thenReturn(0);
        new BacktestExperimentDispatcher(experiments,candidates,mock(BacktestRunMapper.class),mock(BacktestRunService.class),mock(BacktestExperimentService.class),properties(2),new ObjectMapper()).tick();
        verify(candidates).claimNextPending(anyString(),anyString(),any());verify(candidates,never()).findClaimed(anyString(),anyString());
    }
    private QuantExperimentProperties properties(int active){QuantExperimentProperties p=new QuantExperimentProperties();p.setMaxActiveCandidatesPerExperiment(active);return p;}
    private BacktestExperimentRow row(){BacktestExperimentRow row=new BacktestExperimentRow();row.experimentId=UUID.randomUUID().toString();row.datasetId=1;row.strategyCode="EMA_CROSS_LONG_ONLY";row.strategyVersion="1.0.0";row.trainingStartOpenTimeMs=0;row.trainingEndOpenTimeMs=60000;row.validationStartOpenTimeMs=60000;row.validationEndOpenTimeMs=120000;row.orderAmount=java.math.BigDecimal.ONE;row.feeRate=new java.math.BigDecimal("0.001");return row;}
}
