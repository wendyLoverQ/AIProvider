package com.aiprovider.quant.execution;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyException;
import java.math.BigDecimal;
import java.util.Map;

public final class BacktestCompatibilityService {
    private static final BigDecimal MAX_FEE_RATE = new BigDecimal("0.01");
    private final ExecutionProfileRegistry profiles;

    public BacktestCompatibilityService(ExecutionProfileRegistry profiles) {
        this.profiles = profiles;
    }

    public ValidatedExecutionContext validate(
            String profileCode,
            String directionMode,
            String orderSizingMode,
            QuantStrategyDefinition strategy,
            BacktestMarketContext market,
            Map<String, Integer> parameters,
            BigDecimal orderAmount,
            BigDecimal feeRate) {
        ExecutionProfileDefinition profile = profiles.get(profileCode);
        DirectionMode direction = parseDirection(directionMode);
        OrderSizingMode sizing = parseSizing(orderSizingMode);
        return validate(profile, direction, sizing, strategy, market, parameters, orderAmount, feeRate);
    }

    public ValidatedExecutionContext validate(
            ExecutionProfileCode profileCode,
            DirectionMode directionMode,
            OrderSizingMode orderSizingMode,
            QuantStrategyDefinition strategy,
            BacktestMarketContext market,
            Map<String, Integer> parameters,
            BigDecimal orderAmount,
            BigDecimal feeRate) {
        if (profileCode == null) {
            throw error("BACKTEST_EXECUTION_PROFILE_REQUIRED", "executionProfileCode is required");
        }
        return validate(
                profiles.get(profileCode),
                directionMode,
                orderSizingMode,
                strategy,
                market,
                parameters,
                orderAmount,
                feeRate);
    }

    private ValidatedExecutionContext validate(
            ExecutionProfileDefinition profile,
            DirectionMode direction,
            OrderSizingMode sizing,
            QuantStrategyDefinition strategy,
            BacktestMarketContext market,
            Map<String, Integer> parameters,
            BigDecimal orderAmount,
            BigDecimal feeRate) {
        if (market == null || market.marketType() == null) {
            throw error("BACKTEST_MARKET_EXECUTION_INCOMPATIBLE", "market context is required");
        }
        if (profile.marketType() != market.marketType()) {
            throw error(
                    "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE",
                    "profileMarketType=" + profile.marketType() + " datasetMarketType=" + market.marketType());
        }
        if (direction == null || direction != profile.directionMode()) {
            throw error("BACKTEST_DIRECTION_INCOMPATIBLE", "directionMode=" + direction);
        }
        if (sizing == null || sizing != profile.orderSizingMode()) {
            throw error("BACKTEST_ORDER_SIZING_INCOMPATIBLE", "orderSizingMode=" + sizing);
        }
        if (strategy == null || !strategy.supportedMarketTypes().contains(market.marketType())) {
            throw error(
                    "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE",
                    "strategy does not support marketType=" + market.marketType());
        }
        if (!strategy.supportedExecutionProfiles().contains(profile.code())) {
            throw error(
                    "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                    "strategy does not support executionProfileCode=" + profile.code());
        }
        if (!strategy.supportedDirectionModes().contains(direction)) {
            throw error(
                    "BACKTEST_DIRECTION_INCOMPATIBLE",
                    "strategy does not support directionMode=" + direction);
        }
        if (!market.availableFeatures().containsAll(profile.requiredMarketFeatures())
                || !market.availableFeatures().containsAll(strategy.requiredMarketFeatures())) {
            throw error(
                    "BACKTEST_MARKET_FEATURE_MISSING",
                    "availableFeatures=" + market.availableFeatures());
        }
        if (orderAmount == null || orderAmount.signum() <= 0) {
            throw error("BACKTEST_PARAMETER_INVALID", "orderAmount must be positive");
        }
        if (feeRate == null
                || feeRate.signum() < 0
                || feeRate.compareTo(MAX_FEE_RATE) > 0) {
            throw error("BACKTEST_PARAMETER_INVALID", "feeRate must be between 0 and 0.01");
        }
        try {
            strategy.minimumRequiredBars(parameters == null ? Map.of() : parameters);
        } catch (StrategyException exception) {
            throw new BacktestException(
                    exception.getErrorCode(), exception.getMessage(), exception);
        }
        return new ValidatedExecutionContext(profile, direction, sizing);
    }

    private DirectionMode parseDirection(String value) {
        try {
            return value == null ? null : DirectionMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw error("BACKTEST_DIRECTION_INCOMPATIBLE", "directionMode=" + value);
        }
    }

    private OrderSizingMode parseSizing(String value) {
        try {
            return value == null ? null : OrderSizingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw error("BACKTEST_ORDER_SIZING_INCOMPATIBLE", "orderSizingMode=" + value);
        }
    }

    private static BacktestException error(String code, String message) {
        return new BacktestException(code, message);
    }

    public record ValidatedExecutionContext(
            ExecutionProfileDefinition profile,
            DirectionMode directionMode,
            OrderSizingMode orderSizingMode) {}
}
