package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BacktestExperimentRecoveryServiceTest {
    @Test void resetsConfiguredStaleClaimsThenRunsDispatcher(){
        BacktestExperimentCandidateMapper candidates=mock(BacktestExperimentCandidateMapper.class);BacktestExperimentDispatcher dispatcher=mock(BacktestExperimentDispatcher.class);QuantExperimentProperties properties=new QuantExperimentProperties();properties.setStaleClaimSeconds(300);
        new BacktestExperimentRecoveryService(candidates,dispatcher,properties).recover();
        verify(candidates).resetStaleClaims(argThat(cutoff->cutoff.isBefore(Instant.now().minusSeconds(299))),any());verify(dispatcher).tick();
    }
}
