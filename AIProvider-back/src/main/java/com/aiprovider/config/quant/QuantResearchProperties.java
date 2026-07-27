package com.aiprovider.config.quant;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "quant.research")
@Validated
public class QuantResearchProperties {
  @Min(1000) @Max(60000) private long aggregationIntervalMs = 3000;
  @Min(1) @Max(200) private int batchSize = 50;
  @Min(1) @Max(64) private int maxCandidates = 64;
  @Min(1000) @Max(60000) private long oosRecoveryIntervalMs = 5000;
  @Min(1) @Max(100) private int oosRecoveryBatchSize = 20;
  public long getAggregationIntervalMs() { return aggregationIntervalMs; }
  public void setAggregationIntervalMs(long value) { aggregationIntervalMs = value; }
  public int getBatchSize() { return batchSize; }
  public void setBatchSize(int value) { batchSize = value; }
  public int getMaxCandidates() { return maxCandidates; }
  public void setMaxCandidates(int value) { maxCandidates = value; }
  public long getOosRecoveryIntervalMs() { return oosRecoveryIntervalMs; }
  public void setOosRecoveryIntervalMs(long value) { oosRecoveryIntervalMs = value; }
  public int getOosRecoveryBatchSize() { return oosRecoveryBatchSize; }
  public void setOosRecoveryBatchSize(int value) { oosRecoveryBatchSize = value; }
}
