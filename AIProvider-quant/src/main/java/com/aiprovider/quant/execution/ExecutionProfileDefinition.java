package com.aiprovider.quant.execution;

import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;
import java.util.HashSet;
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
        requireNonNull(code, "code");
        requireText(name, "name");
        requireText(description, "description");
        requireNonNull(marketType, "marketType");
        requireNonNull(directionMode, "directionMode");
        requireNonNull(orderSizingMode, "orderSizingMode");
        requireNonNull(positionSide, "positionSide");
        requireNonNull(entryOrderSide, "entryOrderSide");
        requireNonNull(exitOrderSide, "exitOrderSide");
        requireNonNull(leverage, "leverage");
        if (leverage.signum() <= 0) {
            throw new IllegalArgumentException("leverage must be greater than zero");
        }
        requireText(fillModel, "fillModel");
        requireText(transactionCostModel, "transactionCostModel");
        requireText(holdingCostModel, "holdingCostModel");
        requireText(fundingCostModel, "fundingCostModel");
        requireText(liquidationModel, "liquidationModel");
        requireText(marginModel, "marginModel");
        requireNonNull(requiredMarketFeatures, "requiredMarketFeatures");
        if (requiredMarketFeatures.isEmpty()) {
            throw new IllegalArgumentException("requiredMarketFeatures must not be empty");
        }
        for (MarketFeature feature : requiredMarketFeatures) {
            if (feature == null) {
                throw new IllegalArgumentException(
                        "requiredMarketFeatures must not contain null");
            }
        }
        requireNonNull(limitations, "limitations");
        if (limitations.isEmpty()) {
            throw new IllegalArgumentException("limitations must not be empty");
        }
        for (int index = 0; index < limitations.size(); index++) {
            requireText(limitations.get(index), "limitations[" + index + "]");
        }
        if (new HashSet<>(limitations).size() != limitations.size()) {
            throw new IllegalArgumentException("limitations must not contain duplicates");
        }
        if (directionMode != DirectionMode.LONG_ONLY) {
            throw new IllegalArgumentException("directionMode must be LONG_ONLY");
        }
        if (positionSide != PositionSide.LONG) {
            throw new IllegalArgumentException("positionSide must be LONG for LONG_ONLY");
        }
        if (entryOrderSide != OrderSide.BUY) {
            throw new IllegalArgumentException("entryOrderSide must be BUY for LONG_ONLY");
        }
        if (exitOrderSide != OrderSide.SELL) {
            throw new IllegalArgumentException("exitOrderSide must be SELL for LONG_ONLY");
        }
        requiredMarketFeatures = Set.copyOf(requiredMarketFeatures);
        limitations = List.copyOf(limitations);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
