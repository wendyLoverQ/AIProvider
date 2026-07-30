package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultRuntimeMarketStateEngineRestoreTest {
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");
    private static final RuntimeMarketKey KEY = new RuntimeMarketKey(
            MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL, "BTCUSDT", KlineInterval.M1);
    private final DefaultRuntimeMarketStateEngine engine = new DefaultRuntimeMarketStateEngine();

    @Test
    void restoresEmptyWatermarksAndCompleteState() {
        RuntimeMarketState empty = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        RuntimeMarketState restoredEmpty = engine.restore(request(empty));
        assertThat(restoredEmpty).isEqualTo(empty);

        RuntimeMarketState state = engine.onKline(empty, kline(1, true)).getState();
        state = engine.onBookTicker(state, book(2)).getState();
        state = engine.onMarkPrice(state, mark(BASE.plusSeconds(10), "101.25")).getState();
        assertThat(engine.restore(request(state))).isEqualTo(state);
    }

    @Test
    void restoredStateRetainsDuplicateAndOldEventSemantics() {
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.singletonList(candle(0))), kline(1, true)).getState();
        state = engine.onBookTicker(state, book(2)).getState();
        state = engine.onMarkPrice(state, mark(BASE.plusSeconds(10), "101.25")).getState();
        RuntimeMarketState restored = engine.restore(request(state));

        assertThat(engine.onKline(restored, kline(1, true)).getUpdateType())
                .isEqualTo(RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED);
        assertThatThrownBy(() -> engine.onKline(restored, kline(0, true)))
                .isInstanceOf(RuntimeMarketStateException.class)
                .extracting(e -> ((RuntimeMarketStateException) e).getErrorCode())
                .isEqualTo(RuntimeMarketStateException.EVENT_TIME_INVALID);
        assertThat(engine.onBookTicker(restored, book(2)).getUpdateType())
                .isEqualTo(RuntimeMarketUpdateType.DUPLICATE_TOP_OF_BOOK_IGNORED);
        assertThat(engine.onMarkPrice(restored, mark(BASE.plusSeconds(10), "101.25")).getUpdateType())
                .isEqualTo(RuntimeMarketUpdateType.DUPLICATE_MARK_PRICE_IGNORED);
    }

    @Test
    void rejectsInvalidWindowContextOrderingCapacityAndWatermarks() {
        RuntimeMarketState state = engine.initialize(KEY, 3,
                Arrays.asList(candle(0), candle(1)));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 1, state.getClosedCandles(),
                null, null, null, null, null, null, null, null));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 2,
                Arrays.asList(state.getClosedCandles().get(1), state.getClosedCandles().get(0)),
                null, null, null, null, null, null, null, null));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3,
                Arrays.asList(state.getClosedCandles().get(0), RuntimeClosedCandle.from(candle(2))),
                null, null, null, null, null, null, null, null));
        RuntimeClosedCandle wrongContext = new RuntimeClosedCandle(
                MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL, "ETHUSDT", KlineInterval.M1,
                BASE.plusSeconds(120), BASE.plusSeconds(60), BASE.plusSeconds(119).plusMillis(999),
                new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("99"),
                new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE, 1, BigDecimal.ONE, BigDecimal.ONE);
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3,
                Arrays.asList(state.getClosedCandles().get(0), wrongContext),
                null, null, null, null, null, null, null, null));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3, state.getClosedCandles(),
                null, null, BASE.plusSeconds(5), null, null, null, null, null));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3, state.getClosedCandles(),
                null, null, null, null, null, "", null, null));
    }

    @Test
    void rejectsKlineAndTopOfBookMarkWatermarkMismatches() {
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.singletonList(candle(0))), kline(1, true)).getState();
        RuntimeTopOfBook book = new RuntimeTopOfBook(KEY.getProvider(), KEY.getMarketType(), KEY.getSymbol(),
                BASE.plusSeconds(20), new BigDecimal("100"), new BigDecimal("2"),
                new BigDecimal("101"), new BigDecimal("2"));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3, state.getClosedCandles(),
                book, null, state.getLastKlineEventTime(), BASE.plusSeconds(21), null,
                state.getLastKlineEventFingerprint(), "book", null));
        assertRestoreInvalid(new RuntimeMarketStateRestoreRequest(KEY, 3, state.getClosedCandles(),
                null, null, state.getClosedCandles().get(1).getEventTime().minusMillis(1), null, null,
                "kline", null, null));
    }

    @Test
    void restoreRequestCopiesItsCandleList() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        List<RuntimeClosedCandle> source = new ArrayList<>(state.getClosedCandles());
        RuntimeMarketStateRestoreRequest request = new RuntimeMarketStateRestoreRequest(KEY, 3, source,
                null, null, null, null, null, null, null, null);
        source.clear();
        assertThat(request.getClosedCandles()).hasSize(1);
        assertThatThrownBy(() -> request.getClosedCandles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private RuntimeMarketStateRestoreRequest request(RuntimeMarketState state) {
        return new RuntimeMarketStateRestoreRequest(KEY, state.getMaxClosedCandles(), state.getClosedCandles(),
                state.getLatestTopOfBook(), state.getLatestMarkPrice(), state.getLastKlineEventTime(),
                state.getLastBookTickerEventTime(), state.getLastMarkPriceEventTime(),
                state.getLastKlineEventFingerprint(), state.getLastBookTickerEventFingerprint(),
                state.getLastMarkPriceEventFingerprint());
    }

    private void assertRestoreInvalid(RuntimeMarketStateRestoreRequest request) {
        assertThatThrownBy(() -> engine.restore(request)).isInstanceOf(RuntimeMarketStateException.class)
                .extracting(e -> ((RuntimeMarketStateException) e).getErrorCode())
                .isEqualTo(RuntimeMarketStateException.RESTORE_INVALID);
    }

    private static HistoricalCandle candle(int index) {
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(KEY.getProvider());
        candle.setMarketType(KEY.getMarketType());
        candle.setSymbol(KEY.getSymbol());
        candle.setInterval(KEY.getInterval());
        candle.setOpenTime(BASE.plusSeconds(index * 60L));
        candle.setCloseTime(BASE.plusSeconds((index + 1L) * 60L).minusMillis(1));
        candle.setOpenPrice(new BigDecimal("100.0"));
        candle.setHighPrice(new BigDecimal("102.0"));
        candle.setLowPrice(new BigDecimal("99.0"));
        candle.setClosePrice(new BigDecimal("101.0"));
        candle.setVolume(new BigDecimal("10.0"));
        candle.setQuoteVolume(new BigDecimal("1005.0"));
        candle.setTradeCount(12L);
        candle.setTakerBuyBaseVolume(new BigDecimal("6.0"));
        candle.setTakerBuyQuoteVolume(new BigDecimal("603.0"));
        return candle;
    }

    private static StreamKlineEvent kline(int index, boolean closed) {
        HistoricalCandle source = candle(index);
        StreamKlineEvent event = new StreamKlineEvent();
        event.setProvider(source.getProvider());
        event.setMarketType(source.getMarketType());
        event.setSymbol(source.getSymbol());
        event.setInterval(source.getInterval());
        event.setEventTime(source.getCloseTime().plusMillis(1));
        event.setOpenTime(source.getOpenTime());
        event.setCloseTime(source.getCloseTime());
        event.setOpen(source.getOpenPrice());
        event.setHigh(source.getHighPrice());
        event.setLow(source.getLowPrice());
        event.setClose(source.getClosePrice());
        event.setVolume(source.getVolume());
        event.setQuoteVolume(source.getQuoteVolume());
        event.setTradeCount(source.getTradeCount());
        event.setTakerBuyBaseVolume(source.getTakerBuyBaseVolume());
        event.setTakerBuyQuoteVolume(source.getTakerBuyQuoteVolume());
        event.setClosed(closed);
        return event;
    }

    private static StreamBookTickerEvent book(int index) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(KEY.getProvider());
        event.setMarketType(KEY.getMarketType());
        event.setSymbol(KEY.getSymbol());
        event.setEventTime(BASE.plusSeconds(index + 1L));
        event.setBidPrice(new BigDecimal("100.0"));
        event.setBidQuantity(new BigDecimal("2.0"));
        event.setAskPrice(new BigDecimal("101.0"));
        event.setAskQuantity(new BigDecimal("2.5"));
        return event;
    }

    private static StreamMarkPriceEvent mark(Instant eventTime, String price) {
        StreamMarkPriceEvent event = new StreamMarkPriceEvent();
        event.setProvider(KEY.getProvider());
        event.setMarketType(KEY.getMarketType());
        event.setSymbol(KEY.getSymbol());
        event.setEventTime(eventTime);
        event.setMarkPrice(new BigDecimal(price));
        event.setIndexPrice(new BigDecimal("101.00"));
        event.setEstimatedSettlePrice(new BigDecimal("100.50"));
        event.setLastFundingRate(new BigDecimal("-0.0001"));
        event.setInterestRate(new BigDecimal("0.0001"));
        event.setNextFundingTime(BASE.plusSeconds(3600));
        return event;
    }
}
