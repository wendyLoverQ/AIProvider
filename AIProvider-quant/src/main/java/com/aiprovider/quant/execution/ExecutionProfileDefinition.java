package com.aiprovider.quant.execution;

import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record ExecutionProfileDefinition(
        ExecutionProfileCode code,
        String name,
        String description,
        MarketType marketType,
        DirectionMode directionMode,
        OrderSizingMode orderSizingMode,
        PositionSide positionSide,
        OrderSide entryOrderSide,
        OrderSide exitOrderSide,
        BigDecimal leverage,
        String fillModel,
        String transactionCostModel,
        String holdingCostModel,
        String fundingCostModel,
        String liquidationModel,
        String marginModel,
        Set<MarketFeature> requiredMarketFeatures,
        List<String> limitations) {

    public ExecutionProfileDefinition {
        requiredMarketFeatures = Set.copyOf(requiredMarketFeatures);
        limitations = List.copyOf(limitations);
    }
}
