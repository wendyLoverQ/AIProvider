package com.aiprovider.service.quant.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BacktestRunCommand(String runId, long datasetId, Instant startOpenTimeInclusive,
        Instant endOpenTimeExclusive, String strategyCode, String strategyVersion,
        Map<String, Integer> strategyParameters, BigDecimal orderAmount, BigDecimal feeRate,
        boolean forceCloseAtEnd) {
    public BacktestRunCommand {
        strategyParameters = strategyParameters == null ? Map.of() : Map.copyOf(strategyParameters);
    }
}
