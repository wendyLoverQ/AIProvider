package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Pure deterministic state transition engine for one market stream. */
public final class DefaultRuntimeMarketStateEngine implements RuntimeMarketStateEngine {
    private static final String MARK_PRICE_CONFLICT = "RUNTIME_MARKET_MARK_PRICE_CONFLICT";

    @Override
    public RuntimeMarketState initialize(RuntimeMarketKey key, int maxClosedCandles,
                                         List<HistoricalCandle> seedCandles) {
        if (key == null || seedCandles == null || maxClosedCandles < 2) {
            throw failure(RuntimeMarketStateException.REQUEST_INVALID,
                    "key and seedCandles are required and maxClosedCandles must be at least 2");
        }
        List<RuntimeClosedCandle> validated = new ArrayList<>(seedCandles.size());
        RuntimeClosedCandle previous = null;
        for (HistoricalCandle seed : seedCandles) {
            RuntimeClosedCandle current = RuntimeClosedCandle.from(seed);
            requireCandleContext(key, current);
            if (previous != null) {
                Instant expected = previous.getOpenTime().plusMillis(key.getInterval().durationMillis());
                int order = current.getOpenTime().compareTo(expected);
                if (order < 0) {
                    throw failure(RuntimeMarketStateException.CANDLE_UNSORTED,
                            "seed candles must be strictly sorted without duplicates");
                }
                if (order > 0) {
                    throw failure(RuntimeMarketStateException.CANDLE_GAP,
                            "seed candles must be continuous");
                }
            }
            validated.add(current);
            previous = current;
        }
        int fromIndex = Math.max(0, validated.size() - maxClosedCandles);
        List<RuntimeClosedCandle> window = new ArrayList<>(validated.subList(fromIndex, validated.size()));
        return new RuntimeMarketState(key, maxClosedCandles, window, null, null,
                null, null, null, null, null, null);
    }

    @Override
    public RuntimeMarketUpdateResult onKline(RuntimeMarketState state, StreamKlineEvent event) {
        requireState(state);
        requireKlineContext(state.getKey(), event);
        RuntimeClosedCandle.validateOpenEvent(event);
        String fingerprint = klineFingerprint(event);
        Instant eventTime = event.getEventTime();

        if (state.getLastKlineEventTime() != null) {
            int eventOrder = eventTime.compareTo(state.getLastKlineEventTime());
            if (eventOrder < 0) {
                throw failure(RuntimeMarketStateException.EVENT_TIME_INVALID,
                        "kline eventTime is older than the event watermark");
            }
            if (eventOrder == 0) {
                if (!fingerprint.equals(state.getLastKlineEventFingerprint())) {
                    throw failure(RuntimeMarketStateException.CANDLE_CONFLICT,
                            "same kline eventTime has different content");
                }
                RuntimeMarketUpdateType type = event.isClosed()
                        ? RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED
                        : RuntimeMarketUpdateType.OPEN_KLINE_IGNORED;
                return new RuntimeMarketUpdateResult(type, state, null);
            }
        }

        if (!event.isClosed()) {
            RuntimeMarketState updated = replaceKlineWatermark(state, eventTime, fingerprint,
                    state.getClosedCandles());
            return new RuntimeMarketUpdateResult(RuntimeMarketUpdateType.OPEN_KLINE_IGNORED,
                    updated, null);
        }

        RuntimeClosedCandle incoming = RuntimeClosedCandle.from(event);
        List<RuntimeClosedCandle> candles = state.getClosedCandles();
        if (!candles.isEmpty()) {
            RuntimeClosedCandle last = candles.get(candles.size() - 1);
            if (incoming.getOpenTime().equals(last.getOpenTime())) {
                if (!incoming.hasSameCandleContent(last)) {
                    throw failure(RuntimeMarketStateException.CANDLE_CONFLICT,
                            "closed candle conflicts with the existing candle at the same openTime");
                }
                RuntimeMarketState updated = replaceKlineWatermark(state, eventTime, fingerprint, candles);
                return new RuntimeMarketUpdateResult(
                        RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED, updated, null);
            }
            Instant expected = last.getOpenTime().plusMillis(state.getKey().getInterval().durationMillis());
            int order = incoming.getOpenTime().compareTo(expected);
            if (order < 0) {
                throw failure(RuntimeMarketStateException.CANDLE_UNSORTED,
                        "closed candle openTime is older than the next expected openTime");
            }
            if (order > 0) {
                throw failure(RuntimeMarketStateException.CANDLE_GAP,
                        "closed candle creates a gap in the runtime window");
            }
        }

        List<RuntimeClosedCandle> updatedCandles = new ArrayList<>(candles);
        updatedCandles.add(incoming);
        if (updatedCandles.size() > state.getMaxClosedCandles()) {
            updatedCandles.remove(0);
        }
        RuntimeMarketState updated = replaceKlineWatermark(state, eventTime, fingerprint, updatedCandles);
        return new RuntimeMarketUpdateResult(RuntimeMarketUpdateType.CLOSED_CANDLE_APPENDED,
                updated, incoming);
    }

    @Override
    public RuntimeMarketUpdateResult onBookTicker(RuntimeMarketState state,
                                                   StreamBookTickerEvent event) {
        requireState(state);
        requireBookContext(state.getKey(), event);
        RuntimeTopOfBook incoming = RuntimeTopOfBook.from(event);
        String fingerprint = bookFingerprint(event);
        Instant eventTime = event.getEventTime();

        if (state.getLastBookTickerEventTime() != null) {
            int eventOrder = eventTime.compareTo(state.getLastBookTickerEventTime());
            if (eventOrder < 0) {
                throw failure(RuntimeMarketStateException.EVENT_TIME_INVALID,
                        "book ticker eventTime is older than the event watermark");
            }
            if (eventOrder == 0) {
                if (!fingerprint.equals(state.getLastBookTickerEventFingerprint())) {
                    throw failure(RuntimeMarketStateException.BOOK_CONFLICT,
                            "same book ticker eventTime has different content");
                }
                return new RuntimeMarketUpdateResult(
                        RuntimeMarketUpdateType.DUPLICATE_TOP_OF_BOOK_IGNORED, state, null);
            }
        }

        RuntimeMarketState updated = new RuntimeMarketState(state.getKey(),
                state.getMaxClosedCandles(), state.getClosedCandles(), incoming,
                state.getLatestMarkPrice(), state.getLastKlineEventTime(), eventTime,
                state.getLastMarkPriceEventTime(), state.getLastKlineEventFingerprint(),
                fingerprint, state.getLastMarkPriceEventFingerprint());
        return new RuntimeMarketUpdateResult(RuntimeMarketUpdateType.TOP_OF_BOOK_UPDATED,
                updated, null);
    }

    @Override
    public RuntimeMarketUpdateResult onMarkPrice(
            RuntimeMarketState state, StreamMarkPriceEvent event) {
        requireState(state);
        requireMarkPriceContext(state.getKey(), event);
        RuntimeMarkPrice incoming = RuntimeMarkPrice.from(event);
        String fingerprint = markPriceFingerprint(event);
        Instant eventTime = event.getEventTime();

        if (state.getLastMarkPriceEventTime() != null) {
            int eventOrder = eventTime.compareTo(state.getLastMarkPriceEventTime());
            if (eventOrder < 0) {
                throw failure(RuntimeMarketStateException.EVENT_TIME_INVALID,
                        "mark price eventTime is older than the event watermark");
            }
            if (eventOrder == 0) {
                if (!fingerprint.equals(state.getLastMarkPriceEventFingerprint())) {
                    throw failure(MARK_PRICE_CONFLICT,
                            "same mark price eventTime has different content");
                }
                return new RuntimeMarketUpdateResult(
                        RuntimeMarketUpdateType.DUPLICATE_MARK_PRICE_IGNORED, state, null);
            }
        }

        RuntimeMarketState updated = new RuntimeMarketState(
                state.getKey(), state.getMaxClosedCandles(), state.getClosedCandles(),
                state.getLatestTopOfBook(), incoming, state.getLastKlineEventTime(),
                state.getLastBookTickerEventTime(), eventTime,
                state.getLastKlineEventFingerprint(), state.getLastBookTickerEventFingerprint(),
                fingerprint);
        return new RuntimeMarketUpdateResult(
                RuntimeMarketUpdateType.MARK_PRICE_UPDATED, updated, null);
    }

    private static RuntimeMarketState replaceKlineWatermark(RuntimeMarketState state,
                                                             Instant eventTime,
                                                             String fingerprint,
                                                             List<RuntimeClosedCandle> candles) {
        return new RuntimeMarketState(state.getKey(), state.getMaxClosedCandles(), candles,
                state.getLatestTopOfBook(), state.getLatestMarkPrice(), eventTime,
                state.getLastBookTickerEventTime(), state.getLastMarkPriceEventTime(),
                fingerprint, state.getLastBookTickerEventFingerprint(),
                state.getLastMarkPriceEventFingerprint());
    }

    private static void requireState(RuntimeMarketState state) {
        if (state == null) {
            throw failure(RuntimeMarketStateException.STATE_INVALID, "runtime market state is required");
        }
    }

    private static void requireKlineContext(RuntimeMarketKey key, StreamKlineEvent event) {
        if (event == null || event.getProvider() != key.getProvider()
                || event.getMarketType() != key.getMarketType()
                || !key.getSymbol().equals(event.getSymbol())
                || event.getInterval() != key.getInterval()) {
            throw failure(RuntimeMarketStateException.CONTEXT_MISMATCH,
                    "kline event does not match the runtime market key");
        }
    }

    private static void requireBookContext(RuntimeMarketKey key, StreamBookTickerEvent event) {
        if (event == null || event.getProvider() != key.getProvider()
                || event.getMarketType() != key.getMarketType()
                || !key.getSymbol().equals(event.getSymbol())) {
            throw failure(RuntimeMarketStateException.CONTEXT_MISMATCH,
                    "book ticker event does not match the runtime market key");
        }
    }

    private static void requireMarkPriceContext(
            RuntimeMarketKey key, StreamMarkPriceEvent event) {
        if (event == null || event.getProvider() != key.getProvider()
                || event.getMarketType() != key.getMarketType()
                || !key.getSymbol().equals(event.getSymbol())) {
            throw failure(RuntimeMarketStateException.CONTEXT_MISMATCH,
                    "mark price event does not match the runtime market key");
        }
    }

    private static void requireCandleContext(RuntimeMarketKey key, RuntimeClosedCandle candle) {
        if (candle.getProvider() != key.getProvider()
                || candle.getMarketType() != key.getMarketType()
                || !key.getSymbol().equals(candle.getSymbol())
                || candle.getInterval() != key.getInterval()) {
            throw failure(RuntimeMarketStateException.CONTEXT_MISMATCH,
                    "seed candle does not match the runtime market key");
        }
    }

    private static String klineFingerprint(StreamKlineEvent event) {
        return fingerprint(event.getProvider(), event.getMarketType(), event.getSymbol(),
                event.getInterval(), event.getEventTime(), event.getOpenTime(), event.getCloseTime(),
                event.getOpen(), event.getHigh(), event.getLow(), event.getClose(), event.getVolume(),
                event.getQuoteVolume(), event.getTradeCount(), event.getTakerBuyBaseVolume(),
                event.getTakerBuyQuoteVolume(), event.isClosed());
    }

    private static String bookFingerprint(StreamBookTickerEvent event) {
        return fingerprint(event.getProvider(), event.getMarketType(), event.getSymbol(),
                event.getEventTime(), event.getBidPrice(), event.getBidQuantity(),
                event.getAskPrice(), event.getAskQuantity());
    }

    private static String markPriceFingerprint(StreamMarkPriceEvent event) {
        return numericFingerprint(
                event.getProvider(), event.getMarketType(), event.getSymbol(), event.getEventTime(),
                event.getMarkPrice(), event.getIndexPrice(), event.getEstimatedSettlePrice(),
                event.getLastFundingRate(), event.getInterestRate(), event.getNextFundingTime());
    }

    private static String numericFingerprint(Object... fields) {
        StringBuilder result = new StringBuilder();
        for (Object field : fields) {
            String value;
            if (field == null) value = null;
            else if (field instanceof BigDecimal) {
                value = ((BigDecimal) field).stripTrailingZeros().toPlainString();
            } else if (field instanceof Instant) value = ((Instant) field).toString();
            else value = String.valueOf(field);
            if (value == null) result.append("-1:");
            else result.append(value.length()).append(':').append(value);
            result.append('|');
        }
        return result.toString();
    }

    private static String fingerprint(Object... fields) {
        StringBuilder result = new StringBuilder();
        for (Object field : fields) {
            String value;
            if (field == null) value = null;
            else if (field instanceof BigDecimal) value = ((BigDecimal) field).toPlainString();
            else if (field instanceof Instant) value = ((Instant) field).toString();
            else value = String.valueOf(field);
            if (value == null) result.append("-1:");
            else result.append(value.length()).append(':').append(value);
            result.append('|');
        }
        return result.toString();
    }

    private static RuntimeMarketStateException failure(String code, String message) {
        return new RuntimeMarketStateException(code, message);
    }
}
