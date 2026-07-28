package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.engine.paper.DefaultPaperTradingEngine;
import com.aiprovider.quant.engine.paper.PaperTradingEngine;
import com.aiprovider.quant.engine.paper.PaperTradingException;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingStepResult;
import com.aiprovider.quant.engine.paper.PaperTradingStepType;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.DefaultRuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.market.runtime.RuntimeMarketState;
import com.aiprovider.quant.market.runtime.RuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketStateException;
import com.aiprovider.quant.market.runtime.RuntimeMarketUpdateResult;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperRuntimeEngineTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");

    private final PaperAccountEngine accountEngine = new DefaultPaperAccountEngine();

    @Test
    void initializesSeedAsWarmupWithoutTradingOrTopOfBook() {
        CountingTradingEngine trading = new CountingTradingEngine(new DefaultPaperTradingEngine());
        PaperRuntimeEngine engine = engine(trading);
        PaperRuntimeConfig config = config(6);
        List<HistoricalCandle> seed = candles(100, 99, 98, 97, 98);
        PaperAccountSnapshot account = account(BASE);

        PaperRuntimeSnapshot runtime = engine.initialize(config, seed, account);

        assertThat(runtime.getConfig()).isSameAs(config);
        assertThat(runtime.getMarketState().toHistoricalCandles()).hasSize(5);
        assertThat(runtime.getMarketState().getLatestTopOfBook()).isNull();
        assertThat(runtime.getTradingSession().getPaperAccountSnapshot()).isSameAs(account);
        assertThat(runtime.getTradingSession().getLastEvaluatedCandle()).isNull();
        assertThat(runtime.getLastProcessedEventTime()).isNull();
        assertThat(trading.createCalls).isOne();
        assertThat(trading.evaluateCalls).isZero();
        assertThat(trading.executeCalls).isZero();
    }

    @Test
    void rejectsConfigWhoseMarketKeyDoesNotMatchTradingConfig() {
        RuntimeMarketKey wrong = new RuntimeMarketKey(
                PROVIDER, MARKET_TYPE, "ETHUSDT", KlineInterval.M1);

        assertThatThrownBy(() -> new PaperRuntimeConfig(wrong, 6, tradingConfig()))
                .isInstanceOf(PaperRuntimeException.class)
                .extracting("errorCode")
                .isEqualTo(PaperRuntimeException.PAPER_RUNTIME_CONFIG_INVALID);
    }

    @Test
    void openAndDuplicateClosedKlinesUpdateOnlyMarketState() {
        CountingTradingEngine trading = new CountingTradingEngine(new DefaultPaperTradingEngine());
        PaperRuntimeEngine engine = engine(trading);
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 100, 100, 100, 100));

        PaperRuntimeStepResult open = engine.onKline(initial, kline(5, "100", false));
        assertThat(open.getStepType()).isEqualTo(PaperRuntimeStepType.OPEN_KLINE_IGNORED);
        assertThat(open.getTradingStepResult()).isNull();
        assertThat(trading.evaluateCalls).isZero();

        PaperRuntimeSnapshot fresh = initialize(engine, candles(100, 100, 100, 100, 100));
        StreamKlineEvent closed = kline(5, "100", true);
        PaperRuntimeStepResult processed = engine.onKline(fresh, closed);
        int callsAfterProcessed = trading.evaluateCalls;
        PaperRuntimeStepResult duplicate = engine.onKline(processed.getRuntime(), closed);

        assertThat(processed.getStepType()).isEqualTo(PaperRuntimeStepType.CLOSED_CANDLE_PROCESSED);
        assertThat(processed.getTradingStepResult().getStepType())
                .isEqualTo(PaperTradingStepType.SIGNAL_HOLD);
        assertThat(duplicate.getStepType())
                .isEqualTo(PaperRuntimeStepType.DUPLICATE_CLOSED_CANDLE_IGNORED);
        assertThat(duplicate.getTradingStepResult()).isNull();
        assertThat(trading.evaluateCalls).isEqualTo(callsAfterProcessed);
    }

    @Test
    void closedCandlePassesCompleteRollingWindowAndPreservesHoldSession() {
        CountingTradingEngine trading = new CountingTradingEngine(new DefaultPaperTradingEngine());
        PaperRuntimeEngine engine = engine(trading);
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 100, 100, 100, 100));

        PaperRuntimeStepResult result = engine.onKline(initial, kline(5, "100", true));

        assertThat(trading.lastCandles).hasSize(6);
        assertThat(trading.lastCandles.get(0).getOpenTime()).isEqualTo(BASE);
        assertThat(result.getTradingStepResult().getStepType())
                .isEqualTo(PaperTradingStepType.SIGNAL_HOLD);
        assertThat(result.getRuntime().getTradingSession())
                .isEqualTo(result.getTradingStepResult().getSession());
    }

    @Test
    void realEnginesSubmitThenRequireNewBooksForPartialAndCompleteFill() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 99, 98, 97, 98));
        PaperRuntimeStepResult cachedBook = engine.onBookTicker(
                initial, book(BASE.plusSeconds(299), "98", "99", "10"));

        StreamKlineEvent crossing = kline(5, "99", true);
        crossing.setEventTime(BASE.plusSeconds(360));
        PaperRuntimeStepResult submitted = engine.onKline(cachedBook.getRuntime(), crossing);

        assertThat(submitted.getTradingStepResult().getStepType())
                .isEqualTo(PaperTradingStepType.ENTRY_ORDER_SUBMITTED);
        assertThat(submitted.getRuntime().getTradingSession().getPendingOrderSnapshot())
                .isNotNull();
        assertThat(submitted.getRuntime().getTradingSession().getPaperAccountSnapshot()
                .getPosition().isFlat()).isTrue();

        PaperRuntimeStepResult partial = engine.onBookTicker(
                submitted.getRuntime(), book(BASE.plusSeconds(361), "98", "99", "1"));
        assertThat(partial.getStepType()).isEqualTo(PaperRuntimeStepType.PENDING_ORDER_EXECUTED);
        assertThat(partial.getTradingStepResult().getStepType())
                .isEqualTo(PaperTradingStepType.ORDER_PARTIALLY_FILLED);
        assertThat(partial.getRuntime().getTradingSession().getPendingOrderSnapshot()
                .getStatus()).isEqualTo(ExecutionOrderStatus.PARTIALLY_FILLED);

        PaperRuntimeStepResult filled = engine.onBookTicker(
                partial.getRuntime(), book(BASE.plusSeconds(362), "98", "99", "10"));
        assertThat(filled.getTradingStepResult().getStepType())
                .isEqualTo(PaperTradingStepType.ORDER_FILLED);
        assertThat(filled.getRuntime().getTradingSession().getPendingOrderSnapshot()).isNull();
        assertThat(filled.getRuntime().getTradingSession().getPaperAccountSnapshot()
                .getPosition().isOpen()).isTrue();
    }

    @Test
    void duplicateBookDoesNotFillTwiceAndBookWithoutPendingOnlyUpdatesMarket() {
        CountingTradingEngine trading = new CountingTradingEngine(new DefaultPaperTradingEngine());
        PaperRuntimeEngine engine = engine(trading);
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 100, 100, 100, 100));
        StreamBookTickerEvent book = book(BASE.plusSeconds(301), "99", "100", "1");

        PaperRuntimeStepResult updated = engine.onBookTicker(initial, book);
        PaperRuntimeStepResult duplicate = engine.onBookTicker(updated.getRuntime(), book);

        assertThat(updated.getStepType()).isEqualTo(PaperRuntimeStepType.TOP_OF_BOOK_UPDATED);
        assertThat(duplicate.getStepType())
                .isEqualTo(PaperRuntimeStepType.DUPLICATE_TOP_OF_BOOK_IGNORED);
        assertThat(trading.executeCalls).isZero();
        assertThat(duplicate.getRuntime().getTradingSession())
                .isEqualTo(updated.getRuntime().getTradingSession());
    }

    @Test
    void klineAndBookRollUtcDayAndKeepPendingOrder() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        Instant nearMidnight = Instant.parse("2026-07-28T23:54:00Z");
        List<HistoricalCandle> seed = candlesAt(nearMidnight, 100, 99, 98, 97, 98);
        PaperRuntimeSnapshot initial = engine.initialize(config(6), seed, account(nearMidnight));
        StreamKlineEvent crossing = klineAt(nearMidnight, 5, "99", true);
        crossing.setEventTime(Instant.parse("2026-07-28T23:59:59.999Z"));
        PaperRuntimeSnapshot pending = engine.onKline(initial, crossing).getRuntime();
        assertThat(pending.getTradingSession().getPendingOrderSnapshot()).isNotNull();

        StreamKlineEvent openNextDay = klineAt(nearMidnight, 6, "100", false);
        openNextDay.setEventTime(Instant.parse("2026-07-29T00:00:01Z"));
        PaperRuntimeStepResult klineRolled = engine.onKline(pending, openNextDay);
        assertThat(klineRolled.isUtcTradingDayRolled()).isTrue();
        assertThat(klineRolled.getPreviousUtcDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(klineRolled.getCurrentUtcDate()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(klineRolled.getRuntime().getTradingSession().getPendingOrderSnapshot())
                .isEqualTo(pending.getTradingSession().getPendingOrderSnapshot());

        PaperRuntimeSnapshot fresh = engine.initialize(
                config(6), candles(100, 100, 100, 100, 100), account(BASE));
        PaperRuntimeStepResult bookRolled = engine.onBookTicker(
                fresh, book(Instant.parse("2026-07-29T00:00:02Z"), "99", "100", "1"));
        assertThat(bookRolled.isUtcTradingDayRolled()).isTrue();
    }

    @Test
    void rejectsOlderUtcDateButAllowsIndependentStreamWatermarksToInterleave() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 100, 100, 100, 100));

        assertThatThrownBy(() -> engine.onBookTicker(
                initial, book(Instant.parse("2026-07-27T23:59:59Z"), "99", "100", "1")))
                .isInstanceOf(PaperRuntimeException.class)
                .extracting("errorCode")
                .isEqualTo(PaperRuntimeException.PAPER_RUNTIME_EVENT_DATE_INVALID);

        PaperRuntimeStepResult laterBook = engine.onBookTicker(
                initial, book(BASE.plusSeconds(400), "99", "100", "1"));
        StreamKlineEvent earlierKline = kline(5, "100", false);
        earlierKline.setEventTime(BASE.plusSeconds(350));
        PaperRuntimeStepResult interleaved = engine.onKline(laterBook.getRuntime(), earlierKline);
        assertThat(interleaved.getRuntime().getLastProcessedEventTime())
                .isEqualTo(BASE.plusSeconds(350));
        assertThat(interleaved.getRuntime().getMarketState().getLastBookTickerEventTime())
                .isEqualTo(BASE.plusSeconds(400));
    }

    @Test
    void wrapsMarketAndTradingFailuresWithStableLowerCodeAndCause() {
        RuntimeMarketStateException marketCause = new RuntimeMarketStateException(
                RuntimeMarketStateException.CANDLE_GAP, "gap");
        RuntimeMarketStateEngine brokenMarket = new ThrowingMarketEngine(marketCause);
        DefaultPaperRuntimeEngine marketRuntime = new DefaultPaperRuntimeEngine(
                brokenMarket, new DefaultPaperTradingEngine(), accountEngine);

        assertThatThrownBy(() -> marketRuntime.initialize(config(6), List.of(), account(BASE)))
                .isInstanceOfSatisfying(PaperRuntimeException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED);
                    assertThat(exception.getMessage()).contains(RuntimeMarketStateException.CANDLE_GAP);
                    assertThat(exception.getCause()).isSameAs(marketCause);
                });

        PaperTradingException tradingCause = new PaperTradingException(
                PaperTradingException.PAPER_TRADING_SIGNAL_FAILED, "signal");
        PaperRuntimeEngine tradingRuntime = engine(new ThrowingTradingEngine(tradingCause));
        PaperRuntimeSnapshot initial = tradingRuntime.initialize(
                config(6), candles(100, 100, 100, 100, 100), account(BASE));
        assertThatThrownBy(() -> tradingRuntime.onKline(initial, kline(5, "100", true)))
                .isInstanceOfSatisfying(PaperRuntimeException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED);
                    assertThat(exception.getMessage())
                            .contains(PaperTradingException.PAPER_TRADING_SIGNAL_FAILED);
                    assertThat(exception.getCause()).isSameAs(tradingCause);
                });
    }

    @Test
    void sameRuntimeAndInputProduceEqualBusinessResult() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot initial = initialize(engine, candles(100, 100, 100, 100, 100));
        StreamKlineEvent event = kline(5, "100", true);

        PaperRuntimeStepResult first = engine.onKline(initial, event);
        PaperRuntimeStepResult second = engine.onKline(initial, event);

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(initial.getLastProcessedEventTime()).isNull();
        assertThat(initial.getMarketState().getClosedCandles()).hasSize(5);
    }

    private PaperRuntimeEngine engine(PaperTradingEngine trading) {
        return new DefaultPaperRuntimeEngine(
                new DefaultRuntimeMarketStateEngine(), trading, accountEngine);
    }

    private PaperRuntimeSnapshot initialize(PaperRuntimeEngine engine, List<HistoricalCandle> seed) {
        return engine.initialize(config(6), seed, account(BASE));
    }

    private PaperRuntimeConfig config(int maxCandles) {
        return new PaperRuntimeConfig(
                new RuntimeMarketKey(PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1),
                maxCandles, tradingConfig());
    }

    private PaperTradingSessionConfig tradingConfig() {
        MarketOrderQuantityRules rules = new MarketOrderQuantityRules(
                PROVIDER, MARKET_TYPE, SYMBOL, "USDT", 3, new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("1000"), new BigDecimal("5"));
        PreTradeRiskPolicy risk = new PreTradeRiskPolicy(
                new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.01"),
                new BigDecimal("0.50"), 5);
        SimulatedExecutionPolicy execution =
                new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", BigDecimal.ZERO);
        return new PaperTradingSessionConfig(
                "runtime-session", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                rules, BigDecimal.ONE, risk, execution);
    }

    private PaperAccountSnapshot account(Instant initializedAt) {
        return accountEngine.initialize(
                "runtime-account", PROVIDER, MARKET_TYPE, "USDT", new BigDecimal("10000"),
                initializedAt.atZone(ZoneOffset.UTC).toLocalDate(), initializedAt);
    }

    private List<HistoricalCandle> candles(int... closes) {
        return candlesAt(BASE, closes);
    }

    private List<HistoricalCandle> candlesAt(Instant start, int... closes) {
        List<HistoricalCandle> result = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            result.add(candleAt(start, index, String.valueOf(closes[index])));
        }
        return result;
    }

    private HistoricalCandle candleAt(Instant start, int index, String close) {
        BigDecimal price = new BigDecimal(close);
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET_TYPE);
        candle.setSymbol(SYMBOL);
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(start.plusSeconds(index * 60L));
        candle.setCloseTime(start.plusSeconds((index + 1L) * 60L).minusMillis(1));
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

    private StreamKlineEvent kline(int index, String close, boolean closed) {
        return klineAt(BASE, index, close, closed);
    }

    private StreamKlineEvent klineAt(Instant start, int index, String close, boolean closed) {
        HistoricalCandle source = candleAt(start, index, close);
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

    private StreamBookTickerEvent book(Instant eventTime, String bid, String ask, String quantity) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL);
        event.setEventTime(eventTime);
        event.setBidPrice(new BigDecimal(bid));
        event.setBidQuantity(new BigDecimal(quantity));
        event.setAskPrice(new BigDecimal(ask));
        event.setAskQuantity(new BigDecimal(quantity));
        return event;
    }

    private static final class CountingTradingEngine implements PaperTradingEngine {
        private final PaperTradingEngine delegate;
        private int createCalls;
        private int evaluateCalls;
        private int executeCalls;
        private List<HistoricalCandle> lastCandles;

        private CountingTradingEngine(PaperTradingEngine delegate) {
            this.delegate = delegate;
        }

        @Override
        public PaperTradingSessionSnapshot createSession(
                PaperTradingSessionConfig config, PaperAccountSnapshot account) {
            createCalls++;
            return delegate.createSession(config, account);
        }

        @Override
        public PaperTradingStepResult evaluateClosedCandles(
                PaperTradingSessionSnapshot session, List<HistoricalCandle> candles,
                Instant evaluatedAt) {
            evaluateCalls++;
            lastCandles = List.copyOf(candles);
            return delegate.evaluateClosedCandles(session, candles, evaluatedAt);
        }

        @Override
        public PaperTradingStepResult executePendingOrder(
                PaperTradingSessionSnapshot session, SimulatedTopOfBook topOfBook) {
            executeCalls++;
            return delegate.executePendingOrder(session, topOfBook);
        }
    }

    private static final class ThrowingTradingEngine implements PaperTradingEngine {
        private final PaperTradingException failure;
        private final PaperTradingEngine delegate = new DefaultPaperTradingEngine();

        private ThrowingTradingEngine(PaperTradingException failure) {
            this.failure = failure;
        }

        @Override
        public PaperTradingSessionSnapshot createSession(
                PaperTradingSessionConfig config, PaperAccountSnapshot account) {
            return delegate.createSession(config, account);
        }

        @Override
        public PaperTradingStepResult evaluateClosedCandles(
                PaperTradingSessionSnapshot session, List<HistoricalCandle> candles,
                Instant evaluatedAt) {
            throw failure;
        }

        @Override
        public PaperTradingStepResult executePendingOrder(
                PaperTradingSessionSnapshot session, SimulatedTopOfBook topOfBook) {
            throw failure;
        }
    }

    private static final class ThrowingMarketEngine implements RuntimeMarketStateEngine {
        private final RuntimeMarketStateException failure;

        private ThrowingMarketEngine(RuntimeMarketStateException failure) {
            this.failure = failure;
        }

        @Override
        public RuntimeMarketState initialize(RuntimeMarketKey key, int maxClosedCandles,
                                             List<HistoricalCandle> seedCandles) {
            throw failure;
        }

        @Override
        public RuntimeMarketUpdateResult onKline(
                RuntimeMarketState state, StreamKlineEvent event) {
            throw failure;
        }

        @Override
        public RuntimeMarketUpdateResult onBookTicker(
                RuntimeMarketState state, StreamBookTickerEvent event) {
            throw failure;
        }
    }
}
