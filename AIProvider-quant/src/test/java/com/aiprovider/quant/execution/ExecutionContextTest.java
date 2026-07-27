package com.aiprovider.quant.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyParameterDefinition;
import com.aiprovider.quant.strategy.StrategyRegistry;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
        assertThat(profile.marketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(profile.name()).isEqualTo("USDT 本位永续·只做多·1× V1");
        assertThat(profile.directionMode()).isEqualTo(DirectionMode.LONG_ONLY);
        assertThat(profile.orderSizingMode()).isEqualTo(OrderSizingMode.BASE_QUANTITY);
        assertThat(profile.positionSide()).isEqualTo(PositionSide.LONG);
        assertThat(profile.entryOrderSide()).isEqualTo(OrderSide.BUY);
        assertThat(profile.exitOrderSide()).isEqualTo(OrderSide.SELL);
        assertThat(profile.leverage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(profile.fillModel()).isEqualTo("TA4J_TRADE_ON_NEXT_OPEN");
        assertThat(profile.transactionCostModel()).isEqualTo("LINEAR_FEE_RATE");
        assertThat(profile.holdingCostModel()).isEqualTo("ZERO");
        assertThat(profile.fundingCostModel()).isEqualTo("ZERO_NOT_MODELED");
        assertThat(profile.liquidationModel()).isEqualTo("NONE_NOT_MODELED");
        assertThat(profile.marginModel()).isEqualTo("NONE_NOT_MODELED");
        assertThat(profile.requiredMarketFeatures()).containsExactly(MarketFeature.OHLCV);
        assertThat(profile.limitations())
                .containsExactly(
                        "不计算资金费率",
                        "不计算保证金占用",
                        "不计算强平",
                        "不模拟逐仓或全仓",
                        "orderAmount 按基础资产数量解释");
        assertThatThrownBy(() -> new ExecutionProfileRegistry(java.util.List.of(profile, profile)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profileDefinitionEnforcesCompleteConstructionInvariants() {
        assertThatThrownBy(
                        () ->
                                definition(
                                        " ",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        " ",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("description");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ZERO,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("leverage");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("requiredMarketFeatures");
        Set<MarketFeature> featuresWithNull = new HashSet<>();
        featuresWithNull.add(null);
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        featuresWithNull,
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("requiredMarketFeatures");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of(),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("limitations");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of(" "),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("limitations[0]");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("same", "same"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("duplicates");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.SELL,
                                        OrderSide.SELL))
                .hasMessageContaining("entryOrderSide");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        PositionSide.LONG,
                                        OrderSide.BUY,
                                        OrderSide.BUY))
                .hasMessageContaining("exitOrderSide");
        assertThatThrownBy(
                        () ->
                                definition(
                                        "name",
                                        "description",
                                        BigDecimal.ONE,
                                        Set.of(MarketFeature.OHLCV),
                                        List.of("limit"),
                                        DirectionMode.LONG_ONLY,
                                        null,
                                        OrderSide.BUY,
                                        OrderSide.SELL))
                .hasMessageContaining("positionSide");
    }

    @Test
    void registryRejectsNullCollectionsAndDefinitionsWithoutOverwriting() {
        ExecutionProfileDefinition profile = profiles.list().get(0);
        assertThatThrownBy(() -> new ExecutionProfileRegistry(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profiles");
        assertThatThrownBy(
                        () ->
                                new ExecutionProfileRegistry(
                                        Arrays.asList(profile, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definition");
        assertThatThrownBy(() -> profiles.get(" usdm_perpetual_long_only_1x_v1 "))
                .isInstanceOf(BacktestException.class)
                .extracting(error -> ((BacktestException) error).getErrorCode())
                .isEqualTo("BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED");
        assertThatThrownBy(() -> profiles.get(" "))
                .isInstanceOf(BacktestException.class)
                .extracting(error -> ((BacktestException) error).getErrorCode())
                .isEqualTo("BACKTEST_EXECUTION_PROFILE_REQUIRED");
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
            assertThatThrownBy(() -> definition.supportedMarketTypes().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> definition.supportedExecutionProfiles().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> definition.supportedDirectionModes().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
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
        assertCode(
                "USDM_PERPETUAL_LONG_ONLY_1X_V1",
                "long_only",
                "BASE_QUANTITY",
                new BacktestMarketContext(
                        "BINANCE_USDM",
                        null,
                        "CANDLE",
                        "BTCUSDT",
                        KlineInterval.M1,
                        Set.of(MarketFeature.OHLCV)),
                "BACKTEST_MARKET_EXECUTION_INCOMPATIBLE");
        assertCode(
                unsupported(Set.of(), strategy.supportedExecutionProfiles(), strategy.supportedDirectionModes()),
                market,
                "BACKTEST_STRATEGY_MARKET_INCOMPATIBLE");
        assertCode(
                unsupported(strategy.supportedMarketTypes(), Set.of(), strategy.supportedDirectionModes()),
                market,
                "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE");
        assertCode(
                unsupported(strategy.supportedMarketTypes(), strategy.supportedExecutionProfiles(), Set.of()),
                market,
                "BACKTEST_DIRECTION_INCOMPATIBLE");
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

    private void assertCode(
            QuantStrategyDefinition definition, BacktestMarketContext market, String code) {
        assertThatThrownBy(
                        () ->
                                compatibility.validate(
                                        "USDM_PERPETUAL_LONG_ONLY_1X_V1",
                                        "LONG_ONLY",
                                        "BASE_QUANTITY",
                                        definition,
                                        market,
                                        Map.of("fastPeriod", 5, "slowPeriod", 20),
                                        BigDecimal.ONE,
                                        BigDecimal.ZERO))
                .isInstanceOf(BacktestException.class)
                .extracting(error -> ((BacktestException) error).getErrorCode())
                .isEqualTo(code);
    }

    private QuantStrategyDefinition unsupported(
            Set<MarketType> markets,
            Set<ExecutionProfileCode> executionProfiles,
            Set<DirectionMode> directions) {
        return new QuantStrategyDefinition() {
            @Override public String code() { return strategy.code(); }
            @Override public String name() { return strategy.name(); }
            @Override public String version() { return strategy.version(); }
            @Override public String description() { return strategy.description(); }
            @Override public Set<MarketType> supportedMarketTypes() { return Set.copyOf(markets); }
            @Override public Set<ExecutionProfileCode> supportedExecutionProfiles() {
                return Set.copyOf(executionProfiles);
            }
            @Override public Set<DirectionMode> supportedDirectionModes() {
                return Set.copyOf(directions);
            }
            @Override public List<StrategyParameterDefinition> parameters() {
                return strategy.parameters();
            }
            @Override public int minimumRequiredBars(Map<String, Integer> values) {
                return strategy.minimumRequiredBars(values);
            }
            @Override public StrategyBuildResult build(Map<String, Integer> values, int barCount) {
                return strategy.build(values, barCount);
            }
        };
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

    private ExecutionProfileDefinition definition(
            String name,
            String description,
            BigDecimal leverage,
            Set<MarketFeature> features,
            List<String> limitations,
            DirectionMode direction,
            PositionSide position,
            OrderSide entry,
            OrderSide exit) {
        return new ExecutionProfileDefinition(
                ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1,
                name,
                description,
                MarketType.USDM_PERPETUAL,
                direction,
                OrderSizingMode.BASE_QUANTITY,
                position,
                entry,
                exit,
                leverage,
                "TA4J_TRADE_ON_NEXT_OPEN",
                "LINEAR_FEE_RATE",
                "ZERO",
                "ZERO_NOT_MODELED",
                "NONE_NOT_MODELED",
                "NONE_NOT_MODELED",
                features,
                limitations);
    }
}
