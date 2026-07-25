package com.aiprovider.quant.indicator;

import java.math.BigDecimal;
import java.time.Instant;

public final class IndicatorPoint {
    private final Instant openTime;
    private final BigDecimal value;
    private final boolean stable;
    public IndicatorPoint(Instant openTime, BigDecimal value, boolean stable) { this.openTime = openTime; this.value = value; this.stable = stable; }
    public Instant getOpenTime() { return openTime; }
    public BigDecimal getValue() { return value; }
    public boolean isStable() { return stable; }
}
