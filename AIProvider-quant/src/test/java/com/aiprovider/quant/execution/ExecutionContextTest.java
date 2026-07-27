package com.aiprovider.quant.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyRegistry;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionContextTest {
    private final ExecutionProfileRegistry profiles = new ExecutionProfileRegistry();
    private final BacktestCompatibilityService compatibility =
            new BacktestCompatibilityService(profiles);
    private final QuantStrategyDefinition strategy =
            new StrategyRegistry().get("EMA_CROSS_LONG_ONLY");

    @Test
    void registryHasOneImmutableProfileAndRejectsUnknownAndDuplicateCodes() {
        assertThat(profiles.list())
                .extracting(ExecutionProfileDefinition::code)
                .containsExactly(ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1);
        assertThatThrownBy(() -> profiles.list().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> profiles.get("UNKNOWN"))
                .isInstanceOf(BacktestException.class)
                .extracting(error -> ((BacktestException) error).getErrorCode())
                .isEqualTo("BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED");
        ExecutionProfileDefinition profile = profiles.list().get(0);
        assertThatThrownBy(() -> new ExecutionProfileRegistry(java.util.List.of(profile, profile)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builtInStrategiesPublishCompleteImmutableCapabilities() {
        for (QuantStrategyDefinition definition : new StrategyRegistry().list()) {
            assertThat(definition.supportedMarketTypes())
                    .containsExactly(MarketType.USDM_PERPETUAL);
            assertThat(definition.supportedExecutionProfiles())
                    .containsExactly(ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1);
            assertThat(definition.supportedDirectionModes()).containsExactly(DirectionMode.LONG_ONLY);
            assertThat(definition.requiredMarketFeatures()).containsExactly(MarketFeature.OHLCV);
            assertThatThrownBy(() -> definition.requiredMarketFeatures().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void compatibleContextPassesAndStableIncompatibilityCodesAreReturned() {
        BacktestMarketContext market = market(Set.of(MarketFeature.OHLCV));
        assertThat(
                        compatibility.validate(
                                "USDM_PERPETUAL_LONG_ONLY_1X_V1",
                                "LONG_ONLY",
                                "BASE_QUANTITY",
                                strategy,
                                market,
                                Map.of("fastPeriod", 5, "slowPeriod", 20),
                                BigDecimal.ONE,
                                new BigDecimal("0.001")))
                .extracting(context -> context.profile().code())
                .isEqualTo(ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1);
        assertCode(null, "LONG_ONLY", "BASE_QUANTITY", market, "BACKTEST_EXECUTION_PROFILE_REQUIRED");
        assertCode("UNKNOWN", "LONG_ONLY", "BASE_QUANTITY", market, "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED");
        assertCode("USDM_PERPETUAL_LONG_ONLY_1X_V1", "long_only", "BASE_QUANTITY", market, "BACKTEST_DIRECTION_INCOMPATIBLE");
        assertCode("USDM_PERPETUAL_LONG_ONLY_1X_V1", "LONG_ONLY", "base_quantity", market, "BACKTEST_ORDER_SIZING_INCOMPATIBLE");
        assertCode("USDM_PERPETUAL_LONG_ONLY_1X_V1", "LONG_ONLY", "BASE_QUANTITY", market(Set.of()), "BACKTEST_MARKET_FEATURE_MISSING");
    }

    private void assertCode(
            String profile, String direction, String sizing, BacktestMarketContext market, String code) {
        assertThatThrownBy(
                        () ->
                                compatibility.validate(
                                        profile,
                                        direction,
                                        sizing,
                                        strategy,
                                        market,
                                        Map.of("fastPeriod", 5, "slowPeriod", 20),
                                        BigDecimal.ONE,
                                        BigDecimal.ZERO))
                .isInstanceOf(BacktestException.class)
                .extracting(error -> ((BacktestException) error).getErrorCode())
                .isEqualTo(code);
    }

    private BacktestMarketContext market(Set<MarketFeature> features) {
        return new BacktestMarketContext(
                "BINANCE_USDM",
                MarketType.USDM_PERPETUAL,
                "CANDLE",
                "BTCUSDT",
                KlineInterval.M1,
                features);
    }
}
