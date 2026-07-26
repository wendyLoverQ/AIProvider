package com.aiprovider.config.quant;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "quant.walk-forward")
@Validated
public class QuantWalkForwardProperties {
  @Min(1)
  @Max(100)
  private int maxFolds = 36;

  @Min(2)
  @Max(10_000)
  private int maxTotalChildRuns = 2048;

  @Min(500)
  private long dispatcherFixedDelayMs = 3000;

  @Min(30)
  private long staleClaimSeconds = 300;

  public int getMaxFolds() {
    return maxFolds;
  }

  public void setMaxFolds(int value) {
    maxFolds = value;
  }

  public int getMaxTotalChildRuns() {
    return maxTotalChildRuns;
  }

  public void setMaxTotalChildRuns(int value) {
    maxTotalChildRuns = value;
  }

  public long getDispatcherFixedDelayMs() {
    return dispatcherFixedDelayMs;
  }

  public void setDispatcherFixedDelayMs(long value) {
    dispatcherFixedDelayMs = value;
  }

  public long getStaleClaimSeconds() {
    return staleClaimSeconds;
  }

  public void setStaleClaimSeconds(long value) {
    staleClaimSeconds = value;
  }
}
