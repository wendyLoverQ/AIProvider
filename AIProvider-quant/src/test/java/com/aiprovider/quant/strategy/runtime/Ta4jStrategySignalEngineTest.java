package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ta4jStrategySignalEngineTest {
    private static final String CODE = "EMA_CROSS_LONG_ONLY";
    private static final String VERSION = "1.0.0";
    private static final Map<String, Integer> PARAMETERS = Map.of("fastPeriod", 2, "slowPeriod", 4);

    @Test
    void flatEntersWhenTheLastEmaCrossMatches() {
        StrategySignalDecision decision = engine().evaluate(request(candles(100, 99, 98, 97, 98, 99), StrategyRuntimePosition.FLAT));
        assertThat(decision.getSignalType()).isEqualTo(StrategySignalType.ENTER_LONG);
        assertThat(decision.getReason()).isEqualTo(StrategySignalDecisionReason.ENTRY_RULE_MATCHED);
        assertThat(decision.getSignalCandleIndex()).isEqualTo(5);
        assertThat(decision.getSignalPrice()).isEqualByComparingTo("99");
    }

    @Test
    void longExitsWhenTheLastEmaCrossMatches() {
        StrategySignalDecision decision = engine().evaluate(request(candles(100, 101, 102, 103, 102, 101), StrategyRuntimePosition.LONG));
        assertThat(decision.getSignalType()).isEqualTo(StrategySignalType.EXIT_LONG);
        assertThat(decision.getReason()).isEqualTo(StrategySignalDecisionReason.EXIT_RULE_MATCHED);
    }

    @Test
    void unmatchedRuleProducesHold() {
        StrategySignalDecision decision = engine().evaluate(request(candles(100, 100, 100, 100, 100), StrategyRuntimePosition.FLAT));
        assertThat(decision.getSignalType()).isEqualTo(StrategySignalType.HOLD);
        assertThat(decision.getReason()).isEqualTo(StrategySignalDecisionReason.ENTRY_RULE_NOT_MATCHED);
    }

    @Test
    void sameInputProducesTheSameDecision() {
        StrategySignalRequest request = request(candles(100, 99, 98, 97, 98, 99), StrategyRuntimePosition.FLAT);
        StrategySignalDecision first = engine().evaluate(request);
        StrategySignalDecision second = engine().evaluate(request);
        assertThat(second.getStrategyCode()).isEqualTo(first.getStrategyCode());
        assertThat(second.getStrategyVersion()).isEqualTo(first.getStrategyVersion());
        assertThat(second.getStrategyParameters()).isEqualTo(first.getStrategyParameters());
        assertThat(second.getSignalType()).isEqualTo(first.getSignalType());
        assertThat(second.getReason()).isEqualTo(first.getReason());
        assertThat(second.getSignalOpenTime()).isEqualTo(first.getSignalOpenTime());
        assertThat(second.getSignalPrice()).isEqualByComparingTo(first.getSignalPrice());
    }

    @Test
    void insufficientBarsIsNotHold() {
        assertThatThrownBy(() -> engine().evaluate(request(candles(100, 99), StrategyRuntimePosition.FLAT)))
                .isInstanceOf(StrategySignalException.class)
                .extracting(exception -> ((StrategySignalException) exception).getErrorCode())
                .isEqualTo("STRATEGY_SIGNAL_INSUFFICIENT_BARS");
    }

    @Test
    void gapFailsAsDataInvalid() {
        List<HistoricalCandle> candles = candles(100, 99, 98, 97, 98, 99);
        candles.get(3).setOpenTime(candles.get(3).getOpenTime().plusSeconds(60));
        candles.get(3).setCloseTime(candles.get(3).getOpenTime().plusMillis(59_999));
        assertThatThrownBy(() -> engine().evaluate(request(candles, StrategyRuntimePosition.FLAT)))
                .isInstanceOf(StrategySignalException.class)
                .extracting(exception -> ((StrategySignalException) exception).getErrorCode())
                .isEqualTo("STRATEGY_SIGNAL_DATA_INVALID");
    }

    @Test
    void versionMismatchFails() {
        StrategySignalRequest request = new StrategySignalRequest(CODE, "9.9.9", PARAMETERS, MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL, "BTCUSDT", KlineInterval.M1, candles(100, 99, 98, 97, 98), StrategyRuntimePosition.FLAT);
        assertThatThrownBy(() -> engine().evaluate(request)).isInstanceOf(StrategySignalException.class)
                .extracting(exception -> ((StrategySignalException) exception).getErrorCode())
                .isEqualTo("STRATEGY_SIGNAL_VERSION_NOT_SUPPORTED");
    }

    @Test
    void flatNeverReturnsExit() {
        StrategySignalDecision decision = engine().evaluate(request(candles(100, 101, 102, 103, 102, 101), StrategyRuntimePosition.FLAT));
        assertThat(decision.getSignalType()).isNotEqualTo(StrategySignalType.EXIT_LONG);
    }

    @Test
    void longNeverReturnsSecondEntry() {
        StrategySignalDecision decision = engine().evaluate(request(candles(100, 99, 98, 97, 98, 99), StrategyRuntimePosition.LONG));
        assertThat(decision.getSignalType()).isNotEqualTo(StrategySignalType.ENTER_LONG);
    }

    private StrategySignalEngine engine() { return new Ta4jStrategySignalEngine(); }

    private StrategySignalRequest request(List<HistoricalCandle> candles, StrategyRuntimePosition position) {
        return new StrategySignalRequest(CODE, VERSION, PARAMETERS, MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL, "BTCUSDT", KlineInterval.M1, candles, position);
    }

    private List<HistoricalCandle> candles(int... closes) {
        Instant start = Instant.ofEpochMilli(1_700_000_000_000L);
        List<HistoricalCandle> result = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal price = BigDecimal.valueOf(closes[i]);
            HistoricalCandle candle = new HistoricalCandle();
            candle.setProvider(MarketProviderId.BINANCE_USDM);
            candle.setMarketType(MarketType.USDM_PERPETUAL);
            candle.setSymbol("BTCUSDT");
            candle.setInterval(KlineInterval.M1);
            candle.setOpenTime(start.plusMillis(i * 60_000L));
            candle.setCloseTime(start.plusMillis(i * 60_000L + 59_999L));
            candle.setOpenPrice(price);
            candle.setHighPrice(price);
            candle.setLowPrice(price);
            candle.setClosePrice(price);
            candle.setVolume(BigDecimal.ONE);
            candle.setQuoteVolume(BigDecimal.ONE);
            candle.setTradeCount(1);
            result.add(candle);
        }
        return result;
    }
}
