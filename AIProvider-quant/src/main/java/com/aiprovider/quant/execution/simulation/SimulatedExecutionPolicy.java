package com.aiprovider.quant.execution.simulation;

import java.math.BigDecimal;
import java.util.Objects;

public final class SimulatedExecutionPolicy {
    private static final BigDecimal MAX_FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal MAX_SLIPPAGE_BPS = new BigDecimal("1000");

    private final BigDecimal feeRate;
    private final String feeAsset;
    private final BigDecimal slippageBps;

    public SimulatedExecutionPolicy(BigDecimal feeRate, String feeAsset, BigDecimal slippageBps) {
        if (feeRate == null || feeRate.signum() < 0 || feeRate.compareTo(MAX_FEE_RATE) > 0
                || slippageBps == null || slippageBps.signum() < 0
                || slippageBps.compareTo(MAX_SLIPPAGE_BPS) > 0
                || feeAsset == null || feeAsset.isBlank()) {
            throw new SimulatedExecutionException("SIMULATED_EXECUTION_POLICY_INVALID",
                    "feeRate, feeAsset or slippageBps is invalid");
        }
        this.feeRate = feeRate;
        this.feeAsset = feeAsset;
        this.slippageBps = slippageBps;
    }

    public BigDecimal getFeeRate() { return feeRate; }
    public String getFeeAsset() { return feeAsset; }
    public BigDecimal getSlippageBps() { return slippageBps; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SimulatedExecutionPolicy that)) return false;
        return Objects.equals(feeRate, that.feeRate) && Objects.equals(feeAsset, that.feeAsset)
                && Objects.equals(slippageBps, that.slippageBps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feeRate, feeAsset, slippageBps);
    }
}
