package com.aiprovider.quant.backtest;

import com.aiprovider.quant.execution.DirectionMode;
import com.aiprovider.quant.execution.ExecutionProfileCode;
import com.aiprovider.quant.execution.OrderSizingMode;
import java.util.Map;
import java.math.BigDecimal;

public final class BacktestRequest {
    private final ExecutionProfileCode executionProfileCode;
    private final DirectionMode directionMode;
    private final OrderSizingMode orderSizingMode;
    private final String strategyCode;
    private final String strategyVersion;
    private final Map<String, Integer> strategyParameters;
    private final BigDecimal orderAmount;
    private final BigDecimal feeRate;
    private final boolean forceCloseAtEnd;
    public BacktestRequest(ExecutionProfileCode executionProfileCode, DirectionMode directionMode,
                           OrderSizingMode orderSizingMode, String strategyCode, String strategyVersion,
                           Map<String, Integer> strategyParameters, BigDecimal orderAmount,
                           BigDecimal feeRate, boolean forceCloseAtEnd) {
        this.executionProfileCode = executionProfileCode;
        this.directionMode = directionMode;
        this.orderSizingMode = orderSizingMode;
        this.strategyCode = strategyCode; this.strategyVersion = strategyVersion; this.strategyParameters = strategyParameters == null ? Map.of() : Map.copyOf(strategyParameters); this.orderAmount = orderAmount; this.feeRate = feeRate; this.forceCloseAtEnd = forceCloseAtEnd;
    }
    public ExecutionProfileCode getExecutionProfileCode() { return executionProfileCode; }
    public DirectionMode getDirectionMode() { return directionMode; }
    public OrderSizingMode getOrderSizingMode() { return orderSizingMode; }
    public String getStrategyCode() { return strategyCode; }
    public String getStrategyVersion() { return strategyVersion; }
    public Map<String, Integer> getStrategyParameters() { return strategyParameters; }
    public BigDecimal getOrderAmount() { return orderAmount; }
    public BigDecimal getFeeRate() { return feeRate; }
    public boolean isForceCloseAtEnd() { return forceCloseAtEnd; }
}
