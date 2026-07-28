package com.aiprovider.quant.strategy.runtime;

public interface StrategySignalEngine {
    StrategySignalDecision evaluate(StrategySignalRequest request);
}
