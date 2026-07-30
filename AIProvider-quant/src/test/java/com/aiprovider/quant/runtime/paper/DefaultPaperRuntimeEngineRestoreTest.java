package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperRuntimeEngineRestoreTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-30T00:00:00Z");

    @Test
    void restoresInitialStateAndContinuesFromTheSameMarketWindow() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeConfig config = config();
        PaperRuntimeSnapshot initial = engine.initialize(config, candles(), account());
        PaperRuntimeSnapshot restored = engine.restore(new PaperRuntimeRestoreRequest(
                config, initial.getMarketState(), initial.getTradingSession(), null, null));

        assertThat(restored).isEqualTo(initial);
        assertThat(engine.onBookTicker(restored, book(BASE.plusSeconds(300))).getRuntime())
                .isEqualTo(engine.onBookTicker(initial, book(BASE.plusSeconds(300))).getRuntime());
    }

    @Test
    void restoresCrossStreamOlderLastStepWithoutGlobalWatermarkOrdering() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot initial = engine.initialize(config(), candles(), account());
        PaperRuntimeSnapshot withLaterBook = engine.onBookTicker(
                initial, book(BASE.plusSeconds(302))).getRuntime();
        PaperRuntimeStepResult withEarlierMark = engine.onMarkPrice(
                withLaterBook, mark(BASE.plusSeconds(301)));

        PaperRuntimeSnapshot runtime = withEarlierMark.getRuntime();
        PaperRuntimeSnapshot restored = engine.restore(new PaperRuntimeRestoreRequest(
                runtime.getConfig(), runtime.getMarketState(), runtime.getTradingSession(),
                runtime.getLastProcessedEventTime(), runtime.getLastStepType()));

        assertThat(restored).isEqualTo(runtime);
        assertThat(restored.getLastStepType()).isEqualTo(PaperRuntimeStepType.MARK_PRICE_UPDATED);
        assertThat(restored.getMarketState().getLastBookTickerEventTime())
                .isEqualTo(BASE.plusSeconds(302));
    }

    @Test
    void rejectsStepThatDoesNotMatchItsStreamWatermark() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot runtime = engine.onBookTicker(
                engine.initialize(config(), candles(), account()), book(BASE.plusSeconds(302))).getRuntime();

        assertThatThrownBy(() -> engine.restore(new PaperRuntimeRestoreRequest(
                runtime.getConfig(), runtime.getMarketState(), runtime.getTradingSession(),
                runtime.getLastProcessedEventTime(), PaperRuntimeStepType.MARK_PRICE_UPDATED)))
                .isInstanceOf(PaperRuntimeException.class)
                .extracting("errorCode")
                .isEqualTo(PaperRuntimeException.PAPER_RUNTIME_RESTORE_STEP_MISMATCH);
    }

    private PaperRuntimeConfig config() {
        PaperTradingSessionConfig trading = new PaperTradingSessionConfig("session-1", PROVIDER, MARKET,
                SYMBOL, KlineInterval.M1, "TEST", "1", Map.of("period", 3),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                new MarketOrderQuantityRules(PROVIDER, MARKET, SYMBOL, "USDT", 3,
                        new BigDecimal(".001"), new BigDecimal(".001"), new BigDecimal("1000"),
                        new BigDecimal("5")), BigDecimal.ONE,
                new PreTradeRiskPolicy(new BigDecimal(".9"), new BigDecimal(".9"), new BigDecimal(".01"),
                        new BigDecimal(".5"), 5), new SimulatedExecutionPolicy(new BigDecimal(".001"), "USDT", BigDecimal.ZERO));
        return new PaperRuntimeConfig(new RuntimeMarketKey(PROVIDER, MARKET, SYMBOL, KlineInterval.M1), 6, trading);
    }

    private PaperAccountSnapshot account() {
        return new DefaultPaperAccountEngine().initialize("account-1", PROVIDER, MARKET, "USDT",
                new BigDecimal("10000"), LocalDate.of(2026, 7, 30), BASE);
    }

    private List<HistoricalCandle> candles() {
        return List.of(candle(0), candle(1), candle(2), candle(3), candle(4));
    }

    private HistoricalCandle candle(int index) {
        HistoricalCandle candle = new HistoricalCandle();
        BigDecimal price = BigDecimal.valueOf(100 - index);
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET);
        candle.setSymbol(SYMBOL);
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(BASE.plusSeconds(index * 60L));
        candle.setCloseTime(BASE.plusSeconds((index + 1L) * 60L).minusMillis(1));
        candle.setOpenPrice(price);
        candle.setHighPrice(price);
        candle.setLowPrice(price);
        candle.setClosePrice(price);
        candle.setVolume(BigDecimal.TEN);
        candle.setQuoteVolume(new BigDecimal("1000"));
        candle.setTradeCount(10);
        candle.setTakerBuyBaseVolume(BigDecimal.ONE);
        candle.setTakerBuyQuoteVolume(new BigDecimal("100"));
        return candle;
    }

    private StreamBookTickerEvent book(Instant time) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(PROVIDER); event.setMarketType(MARKET); event.setSymbol(SYMBOL);
        event.setEventTime(time); event.setBidPrice(new BigDecimal("99"));
        event.setBidQuantity(BigDecimal.TEN); event.setAskPrice(new BigDecimal("100"));
        event.setAskQuantity(BigDecimal.TEN);
        return event;
    }

    private StreamMarkPriceEvent mark(Instant time) {
        StreamMarkPriceEvent event = new StreamMarkPriceEvent();
        event.setProvider(PROVIDER); event.setMarketType(MARKET); event.setSymbol(SYMBOL);
        event.setEventTime(time); event.setMarkPrice(new BigDecimal("101"));
        event.setIndexPrice(new BigDecimal("101")); event.setEstimatedSettlePrice(new BigDecimal("101"));
        event.setLastFundingRate(BigDecimal.ZERO); event.setInterestRate(BigDecimal.ZERO);
        event.setNextFundingTime(time.plusSeconds(3600));
        return event;
    }
}
