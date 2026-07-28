package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRuntimeMarketStateEngineTest {
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");
    private static final RuntimeMarketKey KEY = new RuntimeMarketKey(
            MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL, "BTCUSDT", KlineInterval.M1);
    private final DefaultRuntimeMarketStateEngine engine = new DefaultRuntimeMarketStateEngine();

    @Test
    void initializesEmptySeed() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());

        assertEquals(KEY, state.getKey());
        assertEquals(3, state.getMaxClosedCandles());
        assertEquals(Collections.emptyList(), state.getClosedCandles());
        assertNull(state.getLastKlineEventTime());
    }

    @Test
    void initializesContinuousSeed() {
        RuntimeMarketState state = engine.initialize(KEY, 3,
                Arrays.asList(candle(0), candle(1), candle(2)));

        assertEquals(Arrays.asList(BASE, BASE.plusSeconds(60), BASE.plusSeconds(120)),
                openTimes(state));
    }

    @Test
    void validatesAllSeedThenRetainsNewestWindow() {
        RuntimeMarketState state = engine.initialize(KEY, 2,
                Arrays.asList(candle(0), candle(1), candle(2)));

        assertEquals(Arrays.asList(BASE.plusSeconds(60), BASE.plusSeconds(120)), openTimes(state));

        HistoricalCandle invalidOldCandle = candle(0);
        invalidOldCandle.setHighPrice(BigDecimal.ONE);
        assertError(RuntimeMarketStateException.CANDLE_INVALID,
                () -> engine.initialize(KEY, 2,
                        Arrays.asList(invalidOldCandle, candle(1), candle(2))));
    }

    @Test
    void rejectsUnsortedSeed() {
        assertError(RuntimeMarketStateException.CANDLE_UNSORTED,
                () -> engine.initialize(KEY, 3, Arrays.asList(candle(1), candle(0))));
    }

    @Test
    void rejectsSeedGap() {
        assertError(RuntimeMarketStateException.CANDLE_GAP,
                () -> engine.initialize(KEY, 3, Arrays.asList(candle(0), candle(2))));
    }

    @Test
    void ignoresOpenKlineButAdvancesWatermark() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());
        StreamKlineEvent open = kline(0, false);

        RuntimeMarketUpdateResult result = engine.onKline(state, open);

        assertEquals(RuntimeMarketUpdateType.OPEN_KLINE_IGNORED, result.getUpdateType());
        assertEquals(0, result.getClosedCandleCount());
        assertEquals(open.getEventTime(), result.getState().getLastKlineEventTime());
    }

    @Test
    void appendsNewClosedKline() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));

        RuntimeMarketUpdateResult result = engine.onKline(state, kline(1, true));

        assertEquals(RuntimeMarketUpdateType.CLOSED_CANDLE_APPENDED, result.getUpdateType());
        assertEquals(2, result.getClosedCandleCount());
        assertEquals(BASE.plusSeconds(60), result.getAddedClosedCandle().getOpenTime());
        assertEquals(BASE, result.getWindowStartTime());
        assertEquals(BASE.plusSeconds(120).minusMillis(1), result.getWindowEndTime());
    }

    @Test
    void removesOldestCandleWhenWindowIsFull() {
        RuntimeMarketState state = engine.initialize(KEY, 2, Arrays.asList(candle(0), candle(1)));

        RuntimeMarketState updated = engine.onKline(state, kline(2, true)).getState();

        assertEquals(Arrays.asList(BASE.plusSeconds(60), BASE.plusSeconds(120)), openTimes(updated));
    }

    @Test
    void identicalClosedKlineReplayIsIdempotent() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());
        StreamKlineEvent event = kline(0, true);
        RuntimeMarketUpdateResult first = engine.onKline(state, event);

        RuntimeMarketUpdateResult replay = engine.onKline(first.getState(), event);

        assertEquals(RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED,
                replay.getUpdateType());
        assertEquals(first.getState(), replay.getState());
        assertEquals(1, replay.getClosedCandleCount());
    }

    @Test
    void handsHistoricalSeedToMatchingLiveClosedKline() {
        RuntimeMarketState seeded = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        RuntimeClosedCandle historical = seeded.getClosedCandles().get(0);
        StreamKlineEvent live = kline(0, true);
        Instant originalWindowStart = historical.getOpenTime();
        Instant originalWindowEnd = historical.getCloseTime();

        RuntimeMarketUpdateResult result = engine.onKline(seeded, live);

        assertEquals(RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED,
                result.getUpdateType());
        assertEquals(1, result.getClosedCandleCount());
        assertSame(historical, result.getState().getClosedCandles().get(0));
        assertEquals(seeded.getClosedCandles(), result.getState().getClosedCandles());
        assertEquals(live.getEventTime(), result.getState().getLastKlineEventTime());
        assertNotNull(result.getState().getLastKlineEventFingerprint());
        assertEquals(originalWindowStart, result.getWindowStartTime());
        assertEquals(originalWindowEnd, result.getWindowEndTime());
    }

    @Test
    void rejectsConflictingLiveClosedKlineAtHistoricalSeedOpenTime() {
        RuntimeMarketState seeded = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        StreamKlineEvent conflicting = kline(0, true);
        conflicting.setClose(new BigDecimal("101.5"));

        assertError(RuntimeMarketStateException.CANDLE_CONFLICT,
                () -> engine.onKline(seeded, conflicting));
    }

    @Test
    void candleContentComparisonExcludesOnlyEventTime() {
        StreamKlineEvent firstEvent = kline(0, true);
        StreamKlineEvent laterEvent = kline(0, true);
        laterEvent.setEventTime(firstEvent.getEventTime().plusMillis(1));
        RuntimeClosedCandle first = RuntimeClosedCandle.from(firstEvent);
        RuntimeClosedCandle later = RuntimeClosedCandle.from(laterEvent);

        assertNotEquals(first, later);
        assertTrue(first.hasSameCandleContent(later));
        assertTrue(later.hasSameCandleContent(first));

        StreamKlineEvent changedEvent = kline(0, true);
        changedEvent.setEventTime(firstEvent.getEventTime().plusMillis(1));
        changedEvent.setClose(new BigDecimal("101.5"));
        RuntimeClosedCandle changed = RuntimeClosedCandle.from(changedEvent);

        assertFalse(first.hasSameCandleContent(changed));
        assertFalse(first.hasSameCandleContent(null));
    }

    @Test
    void matchingCandleContentDoesNotBypassKlineEventWatermark() {
        RuntimeMarketState seeded = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        StreamKlineEvent newest = kline(0, true);
        newest.setEventTime(newest.getCloseTime().plusMillis(10));
        RuntimeMarketState handedOff = engine.onKline(seeded, newest).getState();
        StreamKlineEvent older = kline(0, true);
        older.setEventTime(older.getCloseTime().plusMillis(5));

        assertError(RuntimeMarketStateException.EVENT_TIME_INVALID,
                () -> engine.onKline(handedOff, older));
    }

    @Test
    void rejectsChangedPriceAtSameOpenTime() {
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.emptyList()), kline(0, true)).getState();
        StreamKlineEvent changed = kline(0, true);
        changed.setEventTime(changed.getEventTime().plusMillis(1));
        changed.setClose(new BigDecimal("101.5"));

        assertError(RuntimeMarketStateException.CANDLE_CONFLICT,
                () -> engine.onKline(state, changed));
    }

    @Test
    void rejectsNewClosedKlineGap() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));

        assertError(RuntimeMarketStateException.CANDLE_GAP,
                () -> engine.onKline(state, kline(2, true)));
    }

    @Test
    void rejectsOldKlineEvent() {
        StreamKlineEvent open = kline(0, false);
        open.setEventTime(BASE.plusSeconds(500));
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.emptyList()), open).getState();

        assertError(RuntimeMarketStateException.EVENT_TIME_INVALID,
                () -> engine.onKline(state, kline(0, true)));
    }

    @Test
    void rejectsDifferentKlineAtSameEventTime() {
        StreamKlineEvent open = kline(0, false);
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.emptyList()), open).getState();
        StreamKlineEvent changed = kline(0, false);
        changed.setClose(new BigDecimal("101.5"));

        assertError(RuntimeMarketStateException.CANDLE_CONFLICT,
                () -> engine.onKline(state, changed));
    }

    @Test
    void updatesTopOfBook() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());

        RuntimeMarketUpdateResult result = engine.onBookTicker(state, book(0));

        assertEquals(RuntimeMarketUpdateType.TOP_OF_BOOK_UPDATED, result.getUpdateType());
        assertEquals(new BigDecimal("100.0"), result.getLatestTopOfBook().getBidPrice());
        assertEquals(book(0).getEventTime(), result.getState().getLastBookTickerEventTime());
    }

    @Test
    void identicalBookReplayIsIdempotent() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());
        StreamBookTickerEvent event = book(0);
        RuntimeMarketUpdateResult first = engine.onBookTicker(state, event);

        RuntimeMarketUpdateResult replay = engine.onBookTicker(first.getState(), event);

        assertEquals(RuntimeMarketUpdateType.DUPLICATE_TOP_OF_BOOK_IGNORED,
                replay.getUpdateType());
        assertEquals(first.getState(), replay.getState());
    }

    @Test
    void rejectsDifferentBookAtSameEventTime() {
        RuntimeMarketState state = engine.onBookTicker(
                engine.initialize(KEY, 3, Collections.emptyList()), book(0)).getState();
        StreamBookTickerEvent changed = book(0);
        changed.setBidQuantity(new BigDecimal("3.0"));

        assertError(RuntimeMarketStateException.BOOK_CONFLICT,
                () -> engine.onBookTicker(state, changed));
    }

    @Test
    void rejectsOldBookEvent() {
        RuntimeMarketState state = engine.onBookTicker(
                engine.initialize(KEY, 3, Collections.emptyList()), book(1)).getState();

        assertError(RuntimeMarketStateException.EVENT_TIME_INVALID,
                () -> engine.onBookTicker(state, book(0)));
    }

    @Test
    void rejectsCrossedBook() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.emptyList());
        StreamBookTickerEvent crossed = book(0);
        crossed.setBidPrice(new BigDecimal("102.0"));

        assertError(RuntimeMarketStateException.BOOK_INVALID,
                () -> engine.onBookTicker(state, crossed));
    }

    @Test
    void historicalExportReturnsDeepCopies() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));

        List<HistoricalCandle> first = state.toHistoricalCandles();
        List<HistoricalCandle> second = state.toHistoricalCandles();
        first.get(0).setClosePrice(BigDecimal.ONE);
        first.clear();

        assertNotSame(first, second);
        assertEquals(1, second.size());
        assertEquals(new BigDecimal("101.0"), second.get(0).getClosePrice());
        assertEquals(new BigDecimal("101.0"), state.toHistoricalCandles().get(0).getClosePrice());
    }

    @Test
    void mutatingOriginalStreamEventsDoesNotAffectState() {
        StreamKlineEvent kline = kline(0, true);
        RuntimeMarketState state = engine.onKline(
                engine.initialize(KEY, 3, Collections.emptyList()), kline).getState();
        StreamBookTickerEvent book = book(0);
        state = engine.onBookTicker(state, book).getState();

        kline.setClose(BigDecimal.ONE);
        kline.setSymbol("ETHUSDT");
        book.setBidPrice(BigDecimal.ONE);

        assertEquals(new BigDecimal("101.0"), state.getClosedCandles().get(0).getClose());
        assertEquals("BTCUSDT", state.getClosedCandles().get(0).getSymbol());
        assertEquals(new BigDecimal("100.0"), state.getLatestTopOfBook().getBidPrice());
    }

    @Test
    void sameStateAndInputProduceEqualResults() {
        RuntimeMarketState state = engine.initialize(KEY, 3, Collections.singletonList(candle(0)));
        StreamKlineEvent event = kline(1, true);

        RuntimeMarketUpdateResult first = engine.onKline(state, event);
        RuntimeMarketUpdateResult second = engine.onKline(state, event);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static List<Instant> openTimes(RuntimeMarketState state) {
        List<Instant> result = new ArrayList<>();
        for (RuntimeClosedCandle candle : state.getClosedCandles()) {
            result.add(candle.getOpenTime());
        }
        return result;
    }

    private static HistoricalCandle candle(int index) {
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(MarketProviderId.BINANCE_USDM);
        candle.setMarketType(MarketType.USDM_PERPETUAL);
        candle.setSymbol("BTCUSDT");
        candle.setInterval(KlineInterval.M1);
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
        event.setProvider(MarketProviderId.BINANCE_USDM);
        event.setMarketType(MarketType.USDM_PERPETUAL);
        event.setSymbol("BTCUSDT");
        event.setEventTime(BASE.plusSeconds(index + 1L));
        event.setBidPrice(new BigDecimal("100.0"));
        event.setBidQuantity(new BigDecimal("2.0"));
        event.setAskPrice(new BigDecimal("101.0"));
        event.setAskQuantity(new BigDecimal("2.5"));
        return event;
    }

    private static void assertError(String code, Runnable action) {
        RuntimeMarketStateException exception =
                assertThrows(RuntimeMarketStateException.class, action::run);
        assertEquals(code, exception.getErrorCode());
    }
}
