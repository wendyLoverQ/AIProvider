package com.aiprovider.quant.strategy;

import com.aiprovider.quant.execution.DirectionMode;
import com.aiprovider.quant.execution.ExecutionProfileCode;
import com.aiprovider.quant.execution.MarketFeature;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.research.StrategyParameterSpaceProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface QuantStrategyDefinition extends StrategyParameterSpaceProvider {
    String code();
    String name();
    String version();
    String description();
    default Set<MarketType> supportedMarketTypes() {
        return Set.of(MarketType.USDM_PERPETUAL);
    }
    default Set<ExecutionProfileCode> supportedExecutionProfiles() {
        return Set.of(ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1);
    }
    default Set<DirectionMode> supportedDirectionModes() {
        return Set.of(DirectionMode.LONG_ONLY);
    }
    default Set<MarketFeature> requiredMarketFeatures() {
        return Set.of(MarketFeature.OHLCV);
    }
    List<StrategyParameterDefinition> parameters();
    int minimumRequiredBars(Map<String, Integer> values);
    StrategyBuildResult build(Map<String, Integer> values, int barCount);
}
