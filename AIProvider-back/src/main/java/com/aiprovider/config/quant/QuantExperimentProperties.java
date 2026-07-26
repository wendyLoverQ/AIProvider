package com.aiprovider.config.quant;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix="quant.experiment")
@Validated
public class QuantExperimentProperties {
    @Min(1) @Max(64) private int maxCandidates=64;
    @Min(1) @Max(8) private int maxActiveCandidatesPerExperiment=2;
    @Min(500) private long dispatcherFixedDelayMs=2000;
    @Min(30) private long staleClaimSeconds=300;
    public int getMaxCandidates(){return maxCandidates;} public void setMaxCandidates(int v){maxCandidates=v;}
    public int getMaxActiveCandidatesPerExperiment(){return maxActiveCandidatesPerExperiment;} public void setMaxActiveCandidatesPerExperiment(int v){maxActiveCandidatesPerExperiment=v;}
    public long getDispatcherFixedDelayMs(){return dispatcherFixedDelayMs;} public void setDispatcherFixedDelayMs(long v){dispatcherFixedDelayMs=v;}
    public long getStaleClaimSeconds(){return staleClaimSeconds;} public void setStaleClaimSeconds(long v){staleClaimSeconds=v;}
}
