package com.aiprovider.quant.strategy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchStrategyDefinitionTest {
    @Test
    void registryHasStableThreeStrategyOrderAndRejectsUnknown() {
        StrategyRegistry registry = new StrategyRegistry();
        assertThat(registry.list()).extracting(QuantStrategyDefinition::code)
                .containsExactly("EMA_CROSS_LONG_ONLY", "RSI_MEAN_REVERSION_LONG_ONLY", "MACD_TREND_LONG_ONLY");
        assertThat(registry.get("RSI_MEAN_REVERSION_LONG_ONLY")).isInstanceOf(RsiMeanReversionLongOnlyDefinition.class);
        assertThat(registry.get("MACD_TREND_LONG_ONLY")).isInstanceOf(MacdTrendLongOnlyDefinition.class);
        assertThatThrownBy(() -> registry.get("UNKNOWN")).isInstanceOf(StrategyException.class)
                .extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_STRATEGY_NOT_FOUND");
        assertThatThrownBy(() -> registry.register(new RsiMeanReversionLongOnlyDefinition())).isInstanceOf(StrategyException.class)
                .extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("STRATEGY_CODE_DUPLICATE");
    }

    @Test
    void rsiResolvesDefaultsBoundariesAndMinimumBarsWithoutMutatingInput() {
        RsiMeanReversionLongOnlyDefinition definition = new RsiMeanReversionLongOnlyDefinition();
        Map<String, Integer> input = new HashMap<>(Map.of("rsiPeriod", 20));
        assertThat(definition.build(input, 21).getParameters()).containsExactlyInAnyOrderEntriesOf(Map.of("rsiPeriod", 20, "entryThreshold", 30, "exitThreshold", 55));
        assertThat(input).containsExactly(Map.entry("rsiPeriod", 20));
        assertThat(definition.minimumRequiredBars(Map.of())).isEqualTo(15);
        assertThat(definition.minimumRequiredBars(Map.of("rsiPeriod", 500))).isEqualTo(501);
        assertThatThrownBy(() -> definition.build(Map.of("entryThreshold", 55), 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        assertThatThrownBy(() -> definition.build(Map.of("exitThreshold", 51, "entryThreshold", 49), 14)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_INSUFFICIENT_BARS");
        assertThatThrownBy(() -> definition.build(Map.of("other", 1), 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
    }

    @Test
    void macdResolvesDefaultsRelationAndMinimumBarsWithoutMutatingInput() {
        MacdTrendLongOnlyDefinition definition = new MacdTrendLongOnlyDefinition();
        Map<String, Integer> input = new HashMap<>(Map.of("fastPeriod", 5));
        assertThat(definition.build(input, 36).getParameters()).containsExactlyInAnyOrderEntriesOf(Map.of("fastPeriod", 5, "slowPeriod", 26, "signalPeriod", 9));
        assertThat(input).containsExactly(Map.entry("fastPeriod", 5));
        assertThat(definition.minimumRequiredBars(Map.of())).isEqualTo(36);
        assertThat(definition.minimumRequiredBars(Map.of("fastPeriod", 2, "slowPeriod", 3, "signalPeriod", 2))).isEqualTo(6);
        assertThatThrownBy(() -> definition.build(Map.of("fastPeriod", 26, "slowPeriod", 26), 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        assertThatThrownBy(() -> definition.build(Map.of("fastPeriod", 2, "slowPeriod", 3, "signalPeriod", 2), 5)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_INSUFFICIENT_BARS");
        assertThatThrownBy(() -> definition.build(Map.of("other", 1), 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
    }

    @Test
    void rsiRejectsEveryOutOfRangeAndNullParameter() {
        RsiMeanReversionLongOnlyDefinition definition = new RsiMeanReversionLongOnlyDefinition();
        assertThat(definition.build(Map.of("rsiPeriod", 2), 3).getMinimumRequiredBars()).isEqualTo(3);
        assertThat(definition.build(Map.of("rsiPeriod", 500), 501).getMinimumRequiredBars()).isEqualTo(501);
        assertThat(definition.build(Map.of("entryThreshold", 1), 15).getParameters().get("entryThreshold")).isEqualTo(1);
        assertThat(definition.build(Map.of("entryThreshold", 49), 15).getParameters().get("entryThreshold")).isEqualTo(49);
        assertThat(definition.build(Map.of("exitThreshold", 51), 15).getParameters().get("exitThreshold")).isEqualTo(51);
        assertThat(definition.build(Map.of("exitThreshold", 99), 15).getParameters().get("exitThreshold")).isEqualTo(99);
        for (Map<String, Integer> invalid : java.util.List.of(Map.of("rsiPeriod", 1), Map.of("rsiPeriod", 501), Map.of("entryThreshold", 0), Map.of("entryThreshold", 50), Map.of("exitThreshold", 50), Map.of("exitThreshold", 100))) {
            assertThatThrownBy(() -> definition.build(invalid, 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        }
        Map<String, Integer> nullKey = new HashMap<>(); nullKey.put(null, 1);
        Map<String, Integer> nullValue = new HashMap<>(); nullValue.put("rsiPeriod", null);
        assertThatThrownBy(() -> definition.build(nullKey, 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        assertThatThrownBy(() -> definition.build(nullValue, 100)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
    }

    @Test
    void macdRejectsEveryOutOfRangeAndNullParameter() {
        MacdTrendLongOnlyDefinition definition = new MacdTrendLongOnlyDefinition();
        assertThat(definition.build(Map.of("fastPeriod", 2), 36).getParameters().get("fastPeriod")).isEqualTo(2);
        assertThat(definition.build(Map.of("fastPeriod", 500, "slowPeriod", 501), 511).getParameters().get("fastPeriod")).isEqualTo(500);
        assertThat(definition.build(Map.of("fastPeriod", 2, "slowPeriod", 3), 13).getParameters().get("slowPeriod")).isEqualTo(3);
        assertThat(definition.build(Map.of("slowPeriod", 1000), 1010).getParameters().get("slowPeriod")).isEqualTo(1000);
        assertThat(definition.build(Map.of("signalPeriod", 2), 36).getParameters().get("signalPeriod")).isEqualTo(2);
        assertThat(definition.build(Map.of("signalPeriod", 500), 527).getParameters().get("signalPeriod")).isEqualTo(500);
        for (Map<String, Integer> invalid : java.util.List.of(Map.of("fastPeriod", 1), Map.of("fastPeriod", 501), Map.of("slowPeriod", 2), Map.of("slowPeriod", 1001), Map.of("signalPeriod", 1), Map.of("signalPeriod", 501), Map.of("fastPeriod", 26, "slowPeriod", 26))) {
            assertThatThrownBy(() -> definition.build(invalid, 2000)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        }
        Map<String, Integer> nullKey = new HashMap<>(); nullKey.put(null, 1);
        Map<String, Integer> nullValue = new HashMap<>(); nullValue.put("fastPeriod", null);
        assertThatThrownBy(() -> definition.build(nullKey, 2000)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
        assertThatThrownBy(() -> definition.build(nullValue, 2000)).isInstanceOf(StrategyException.class).extracting(e -> ((StrategyException) e).getErrorCode()).isEqualTo("BACKTEST_PARAMETER_INVALID");
    }
}
