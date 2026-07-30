package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategySignalDecisionRestoreTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET = MarketType.USDM_PERPETUAL;
    private static final Instant OPEN = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void restoresAllFieldsWithImmutableParametersAndValueEquality() {
        Map<String, Integer> parameters = new HashMap<>();
        parameters.put("period", 3);
        StrategySignalDecision original = new StrategySignalDecision(
                "TEST", "1", parameters, PROVIDER, MARKET, "BTCUSDT", KlineInterval.M1,
                StrategyRuntimePosition.FLAT, StrategySignalType.ENTER_LONG, 4, candle("100"),
                StrategySignalDecisionReason.ENTRY_RULE_MATCHED);
        StrategySignalDecisionRestoreRequest request = new StrategySignalDecisionRestoreRequest(
                original.getStrategyCode(), original.getStrategyVersion(), parameters,
                original.getProvider(), original.getMarketType(), original.getSymbol(),
                original.getInterval(), original.getCurrentPosition(), original.getSignalType(),
                original.getSignalCandleIndex(), original.getSignalOpenTime(), original.getSignalCloseTime(),
                original.getSignalPrice(), original.getReason());
        parameters.put("period", 9);

        StrategySignalDecision restored = StrategySignalDecision.restore(request);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.hashCode()).isEqualTo(original.hashCode());
        assertThat(restored.getStrategyParameters()).containsEntry("period", 3);
        assertThatThrownBy(() -> restored.getStrategyParameters().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidRestoreFieldsWithStableCode() {
        StrategySignalDecisionRestoreRequest request = new StrategySignalDecisionRestoreRequest(
                "TEST", "1", Map.of(), PROVIDER, MARKET, "BTCUSDT", KlineInterval.M1,
                StrategyRuntimePosition.FLAT, StrategySignalType.HOLD, -1, OPEN,
                OPEN.plusSeconds(60), BigDecimal.ONE, StrategySignalDecisionReason.ENTRY_RULE_NOT_MATCHED);

        assertThatThrownBy(() -> StrategySignalDecision.restore(request))
                .isInstanceOf(StrategySignalException.class)
                .extracting("errorCode")
                .isEqualTo(StrategySignalException.STRATEGY_SIGNAL_RESTORE_INVALID);
    }

    private HistoricalCandle candle(String close) {
        HistoricalCandle candle = new HistoricalCandle();
        BigDecimal price = new BigDecimal(close);
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET);
        candle.setSymbol("BTCUSDT");
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(OPEN);
        candle.setCloseTime(OPEN.plusSeconds(60));
        candle.setOpenPrice(price);
        candle.setHighPrice(price);
        candle.setLowPrice(price);
        candle.setClosePrice(price);
        candle.setVolume(BigDecimal.ONE);
        candle.setQuoteVolume(BigDecimal.ONE);
        candle.setTradeCount(1);
        return candle;
    }
}
