package com.aiprovider.quant.execution;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExecutionProfileRegistry {
    private final Map<ExecutionProfileCode, ExecutionProfileDefinition> definitions =
            new LinkedHashMap<>();

    public ExecutionProfileRegistry() {
        register(defaultProfile());
    }

    public ExecutionProfileRegistry(Collection<ExecutionProfileDefinition> profiles) {
        for (ExecutionProfileDefinition profile : profiles) {
            register(profile);
        }
    }

    private void register(ExecutionProfileDefinition definition) {
        if (definition == null
                || definition.code() == null
                || definitions.putIfAbsent(definition.code(), definition) != null) {
            throw new IllegalArgumentException("duplicate execution profile code");
        }
    }

    public ExecutionProfileDefinition get(ExecutionProfileCode code) {
        ExecutionProfileDefinition definition = definitions.get(code);
        if (definition == null) {
            throw new BacktestException(
                    "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED", "executionProfileCode=" + code);
        }
        return definition;
    }

    public ExecutionProfileDefinition get(String code) {
        if (code == null || code.isBlank()) {
            throw new BacktestException(
                    "BACKTEST_EXECUTION_PROFILE_REQUIRED", "executionProfileCode is required");
        }
        try {
            return get(ExecutionProfileCode.valueOf(code));
        } catch (IllegalArgumentException exception) {
            throw new BacktestException(
                    "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED",
                    "executionProfileCode=" + code,
                    exception);
        }
    }

    public List<ExecutionProfileDefinition> list() {
        return List.copyOf(definitions.values());
    }

    private static ExecutionProfileDefinition defaultProfile() {
        return new ExecutionProfileDefinition(
                ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1,
                "USDM 永续只做多 1×",
                "以基础资产数量下单的 USDM 永续只做多 Ta4j 回测执行模型。",
                MarketType.USDM_PERPETUAL,
                DirectionMode.LONG_ONLY,
                OrderSizingMode.BASE_QUANTITY,
                PositionSide.LONG,
                OrderSide.BUY,
                OrderSide.SELL,
                BigDecimal.ONE,
                "TA4J_TRADE_ON_NEXT_OPEN",
                "LINEAR_FEE_RATE",
                "ZERO",
                "ZERO_NOT_MODELED",
                "NONE_NOT_MODELED",
                "NONE_NOT_MODELED",
                Set.of(MarketFeature.OHLCV),
                List.of(
                        "不计算资金费率",
                        "不计算保证金占用",
                        "不计算强平",
                        "不模拟逐仓或全仓",
                        "orderAmount 按基础资产数量解释"));
    }
}
