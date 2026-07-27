package com.aiprovider.service.quant.model;

import com.aiprovider.quant.execution.DirectionMode;
import com.aiprovider.quant.execution.ExecutionProfileCode;
import com.aiprovider.quant.execution.OrderSizingMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BacktestRunCommand(String runId, long datasetId, Instant startOpenTimeInclusive,
        Instant endOpenTimeExclusive, String strategyCode, String strategyVersion,
        ExecutionProfileCode executionProfileCode, DirectionMode directionMode,
        OrderSizingMode orderSizingMode,
        Map<String, Integer> strategyParameters, BigDecimal orderAmount, BigDecimal feeRate,
        boolean forceCloseAtEnd) {
    public BacktestRunCommand {
        strategyParameters = strategyParameters == null ? Map.of() : Map.copyOf(strategyParameters);
    }
}
