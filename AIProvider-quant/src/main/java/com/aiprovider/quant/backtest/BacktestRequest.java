package com.aiprovider.quant.backtest;

import java.util.Map;

public final class BacktestRequest {
    private final String strategyCode;
    private final String strategyVersion;
    private final Map<String, Integer> strategyParameters;
    private final double orderAmount;
    private final double feeRate;
    private final boolean forceCloseAtEnd;
    public BacktestRequest(String strategyCode, String strategyVersion, Map<String, Integer> strategyParameters, double orderAmount, double feeRate, boolean forceCloseAtEnd) {
        this.strategyCode = strategyCode; this.strategyVersion = strategyVersion; this.strategyParameters = strategyParameters == null ? Map.of() : Map.copyOf(strategyParameters); this.orderAmount = orderAmount; this.feeRate = feeRate; this.forceCloseAtEnd = forceCloseAtEnd;
    }
    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public Map<String, Integer> getStrategyParameters() { return strategyParameters; }
    public double getOrderAmount() { return orderAmount; }
    public double getFeeRate() { return feeRate; }
    public boolean isForceCloseAtEnd() { return forceCloseAtEnd; }
}
