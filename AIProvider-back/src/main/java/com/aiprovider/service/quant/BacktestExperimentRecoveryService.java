package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class BacktestExperimentRecoveryService {
    private final BacktestExperimentCandidateMapper candidates; private final BacktestExperimentDispatcher dispatcher; private final com.aiprovider.config.quant.QuantExperimentProperties properties;
    public BacktestExperimentRecoveryService(BacktestExperimentCandidateMapper c,BacktestExperimentDispatcher d,com.aiprovider.config.quant.QuantExperimentProperties p){candidates=c;dispatcher=d;properties=p;}
    @EventListener(ApplicationReadyEvent.class)
    public void recover(){Instant now=Instant.now();candidates.resetStaleClaims(now.minusSeconds(properties.getStaleClaimSeconds()),now);dispatcher.tick();}
}
