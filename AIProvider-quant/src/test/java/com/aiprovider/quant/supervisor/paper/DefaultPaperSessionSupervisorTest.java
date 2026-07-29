package com.aiprovider.quant.supervisor.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.audit.paper.DefaultPaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.audit.paper.PaperOrderAuditUpdateResult;
import com.aiprovider.quant.engine.paper.DefaultPaperTradingEngine;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.DefaultRuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamStatus;
import com.aiprovider.quant.market.stream.model.StreamStatusEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.aiprovider.quant.market.stream.port.MarketStreamClient;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationViolation;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationViolationCode;
import com.aiprovider.quant.runtime.paper.DefaultPaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeConfig;
import com.aiprovider.quant.runtime.paper.PaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperSessionSupervisorTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");

    @Test void createsFromAConsistentInitialState() {
        Fixture fixture = fixture();
        assertThat(fixture.supervisor.getSnapshot().getState())
                .isEqualTo(PaperSessionSupervisorState.CREATED);
        assertThat(fixture.supervisor.getSnapshot().getLastReconciliationReport().getStatus())
                .isEqualTo(PaperReconciliationStatus.CONSISTENT);
    }

    @Test void rejectsInitialReconciliationInconsistency() {
        assertThatThrownBy(() -> fixture(new DefaultPaperRuntimeEngine(), new InconsistentInitialAudit()))
                .isInstanceOf(PaperSessionSupervisorException.class)
                .extracting("errorCode")
                .isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_INITIAL_STATE_INCONSISTENT);
    }

    @Test void rejectsSessionIdMismatch() {
        Fixture fixture = fixture();
        PaperOrderAuditLedger wrong = new PaperOrderAuditLedger(
                "wrong", PROVIDER, MARKET_TYPE, SYMBOL, List.of(), 0, BASE, BASE);
        assertThatThrownBy(() -> newSupervisor(fixture.runtime, wrong, fixture.audit, fixture.stream))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_CONTEXT_MISMATCH);
    }

    @Test void rejectsNullProviderEventContext() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        StreamBookTickerEvent event = book(BASE.plusSeconds(2), "99", "100", "1");
        event.setProvider(null);
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(event))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_CONTEXT_MISMATCH);
    }

    @Test void rejectsSymbolMismatch() {
        Fixture fixture = fixture();
        PaperOrderAuditLedger wrong = new PaperOrderAuditLedger(
                fixture.runtime.getTradingSession().getConfig().getSessionId(),
                PROVIDER, MARKET_TYPE, "ETHUSDT", List.of(), 0, BASE, BASE);
        assertThatThrownBy(() -> newSupervisor(fixture.runtime, wrong, fixture.audit, fixture.stream))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_CONTEXT_MISMATCH);
    }

    @Test void rejectsInitializedAtBeforeMarketWatermark() {
        Fixture fixture = fixture();
        PaperRuntimeStepResult marked = new DefaultPaperRuntimeEngine().onMarkPrice(
                fixture.runtime, mark(BASE.plusSeconds(301), "101"));
        PaperOrderAuditLedger ledger = ledger(marked.getRuntime());
        assertThatThrownBy(() -> newSupervisor(marked.getRuntime(), ledger, fixture.audit, fixture.stream, BASE))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_REQUEST_INVALID);
    }

    @Test void startsWithRuntimeSubscriptionKey() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThat(fixture.stream.subscribeCount).isOne();
        assertThat(fixture.stream.provider).isEqualTo(PROVIDER);
        assertThat(fixture.stream.symbol).isEqualTo(SYMBOL);
        assertThat(fixture.stream.interval).isEqualTo(KlineInterval.M1);
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.RUNNING);
    }

    @Test void rejectsDuplicateStart() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThatThrownBy(() -> fixture.supervisor.start(BASE.plusSeconds(2)))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_STATE_INVALID);
    }

    @Test void subscribeFailureEntersFailedAndKeepsInitialState() {
        Fixture fixture = fixture();
        fixture.stream.subscribeFailure = new IllegalStateException("connect");
        assertThatThrownBy(() -> fixture.supervisor.start(BASE.plusSeconds(1)))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_STREAM_SUBSCRIBE_FAILED);
        PaperSessionSupervisorSnapshot snapshot = fixture.supervisor.getSnapshot();
        assertThat(snapshot.getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
        assertThat(snapshot.getRuntime()).isSameAs(fixture.runtime);
        assertThat(snapshot.getLedger()).isSameAs(fixture.ledger);
        assertThat(snapshot.getFailure().getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test void stopsCreatedWithoutUnsubscribe() {
        Fixture fixture = fixture();
        PaperSessionSupervisorSnapshot snapshot = fixture.supervisor.stop(BASE.plusSeconds(2));
        assertThat(snapshot.getState()).isEqualTo(PaperSessionSupervisorState.STOPPED);
        assertThat(fixture.stream.unsubscribeCount).isZero();
    }

    @Test void stopsRunningAndUnsubscribesOnce() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        PaperSessionSupervisorSnapshot snapshot = fixture.supervisor.stop(BASE.plusSeconds(2));
        assertThat(snapshot.getState()).isEqualTo(PaperSessionSupervisorState.STOPPED);
        assertThat(fixture.stream.unsubscribeCount).isOne();
    }

    @Test void repeatedStopIsIdempotent() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.stop(BASE.plusSeconds(2));
        fixture.supervisor.stop(BASE.plusSeconds(3));
        assertThat(fixture.stream.unsubscribeCount).isOne();
    }

    @Test void processesKlineRuntimeAuditAndReconciliation() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onKline(kline(BASE.plusSeconds(301), "100", false));
        PaperSessionSupervisorSnapshot snapshot = fixture.supervisor.getSnapshot();
        assertThat(snapshot.getAcceptedRuntimeStepCount()).isOne();
        assertThat(snapshot.getLastEventType()).isEqualTo(PaperSessionSupervisorEventType.KLINE_PROCESSED);
        assertThat(snapshot.getLastRuntimeStepResult()).isNotNull();
        assertThat(snapshot.getLastAuditUpdateResult()).isNotNull();
        assertThat(snapshot.getLastReconciliationReport().getStatus()).isEqualTo(PaperReconciliationStatus.CONSISTENT);
    }

    @Test void processesBookTicker() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(301), "99", "100", "1"));
        assertThat(fixture.supervisor.getSnapshot().getAcceptedRuntimeStepCount()).isOne();
    }

    @Test void processesMarkPrice() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onMarkPrice(mark(BASE.plusSeconds(301), "101"));
        assertThat(fixture.supervisor.getSnapshot().getAcceptedRuntimeStepCount()).isOne();
    }

    @Test void explicitlyIgnoresTicker() {
        CountingRuntime runtime = new CountingRuntime(new DefaultPaperRuntimeEngine());
        Fixture fixture = fixture(runtime);
        fixture.supervisor.start(BASE.plusSeconds(1));
        PaperSessionSupervisorSnapshot before = fixture.supervisor.getSnapshot();
        fixture.supervisor.onTicker(ticker(BASE.plusSeconds(301)));
        PaperSessionSupervisorSnapshot after = fixture.supervisor.getSnapshot();
        assertThat(after.getRuntime()).isSameAs(before.getRuntime());
        assertThat(after.getLedger()).isSameAs(before.getLedger());
        assertThat(after.getIgnoredTickerEventCount()).isOne();
        assertThat(runtime.calls).isZero();
        assertThat(after.getLastEventType()).isEqualTo(PaperSessionSupervisorEventType.TICKER_IGNORED);
    }

    @Test void unchangedOrderLedgerVersionIsPreserved() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        long version = fixture.supervisor.getSnapshot().getLedger().getVersion();
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(301), "99", "100", "1"));
        fixture.supervisor.onMarkPrice(mark(BASE.plusSeconds(302), "101"));
        assertThat(fixture.supervisor.getSnapshot().getLedger().getVersion()).isEqualTo(version);
    }

    @Test void recordsSubmittedOrderInLedger() {
        Fixture fixture = orderFixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(299), "98", "99", "10"));
        StreamKlineEvent crossing = kline(BASE.plusSeconds(360), "99", true);
        fixture.supervisor.onKline(crossing);
        assertThat(fixture.supervisor.getSnapshot().getLedger().getOrderHistory()).hasSize(1);
        assertThat(fixture.supervisor.getSnapshot().getLedger().getVersion()).isEqualTo(1);
    }

    @Test void updatesPartiallyFilledOrderInPlace() {
        Fixture fixture = orderFixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(299), "98", "99", "10"));
        fixture.supervisor.onKline(kline(BASE.plusSeconds(360), "99", true));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(361), "98", "99", "1"));
        ExecutionOrderSnapshot order = fixture.supervisor.getSnapshot().getLedger().getOrderHistory().get(0);
        assertThat(order.getStatus().name()).isEqualTo("PARTIALLY_FILLED");
        assertThat(fixture.supervisor.getSnapshot().getLedger().getVersion()).isEqualTo(2);
    }

    @Test void updatesFullyFilledOrderInPlace() {
        Fixture fixture = orderFixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(299), "98", "99", "10"));
        fixture.supervisor.onKline(kline(BASE.plusSeconds(360), "99", true));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(361), "98", "99", "10"));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(362), "98", "99", "10"));
        assertThat(fixture.supervisor.getSnapshot().getLedger().getOrderHistory().get(0).getStatus().name())
                .isEqualTo("FILLED");
    }

    @Test void reconcilesEveryRuntimeStep() {
        CountingAudit audit = new CountingAudit(new DefaultPaperOrderAuditEngine());
        Fixture fixture = fixture(new DefaultPaperRuntimeEngine(), audit);
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onKline(kline(BASE.plusSeconds(301), "100", false));
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(302), "99", "100", "1"));
        fixture.supervisor.onMarkPrice(mark(BASE.plusSeconds(303), "101"));
        assertThat(audit.reconcileCalls).isEqualTo(4); // constructor plus three accepted steps
    }

    @Test void inconsistentStepIsNotPublished() {
        Fixture fixture = fixture(new DefaultPaperRuntimeEngine(), new InconsistentStepAudit(new DefaultPaperOrderAuditEngine()));
        PaperRuntimeSnapshot oldRuntime = fixture.runtime;
        PaperOrderAuditLedger oldLedger = fixture.ledger;
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(book(BASE.plusSeconds(301), "99", "100", "1")))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_RECONCILIATION_INCONSISTENT);
        assertThat(fixture.supervisor.getSnapshot().getRuntime()).isSameAs(oldRuntime);
        assertThat(fixture.supervisor.getSnapshot().getLedger()).isSameAs(oldLedger);
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
    }

    @Test void runtimeFailureRetainsPreviousState() {
        Fixture fixture = fixture(new ThrowingRuntime());
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(book(BASE.plusSeconds(301), "99", "100", "1")))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_RUNTIME_FAILED);
        assertThat(fixture.supervisor.getSnapshot().getRuntime()).isSameAs(fixture.runtime);
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
    }

    @Test void auditFailureRetainsPreviousState() {
        Fixture fixture = fixture(new DefaultPaperRuntimeEngine(), new ThrowingAudit(new DefaultPaperOrderAuditEngine()));
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(book(BASE.plusSeconds(301), "99", "100", "1")))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_AUDIT_FAILED);
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
    }

    @Test void upstreamFailedStatusFailsAndUnsubscribes() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onStatus(status(StreamStatus.FAILED, BASE.plusSeconds(2), "upstream"));
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
        assertThat(fixture.supervisor.getSnapshot().getFailure().getErrorCode())
                .isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_STREAM_FAILED);
        assertThat(fixture.stream.unsubscribeCount).isOne();
    }

    @Test void statusSavesOnlyImmutableScalars() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.onStatus(status(StreamStatus.LIVE, BASE.plusSeconds(2), "live"));
        assertThat(fixture.supervisor.getSnapshot().getStreamStatus()).isEqualTo(StreamStatus.LIVE);
        assertThat(fixture.supervisor.getSnapshot().getLastStreamStatusAt()).isEqualTo(BASE.plusSeconds(2));
        assertThat(fixture.supervisor.getSnapshot().getLastStreamMessage()).isEqualTo("live");
    }

    @Test void lateEventsDoNotChangeStoppedState() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.supervisor.stop(BASE.plusSeconds(2));
        PaperSessionSupervisorSnapshot before = fixture.supervisor.getSnapshot();
        fixture.supervisor.onBookTicker(book(BASE.plusSeconds(3), "99", "100", "1"));
        assertThat(fixture.supervisor.getSnapshot()).isEqualTo(before);
    }

    @Test void lateEventsDoNotChangeFailedState() {
        Fixture fixture = fixture(new ThrowingRuntime());
        fixture.supervisor.start(BASE.plusSeconds(1));
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(book(BASE.plusSeconds(2), "99", "100", "1")))
                .isInstanceOf(PaperSessionSupervisorException.class);
        PaperSessionSupervisorSnapshot before = fixture.supervisor.getSnapshot();
        fixture.supervisor.onMarkPrice(mark(BASE.plusSeconds(3), "101"));
        assertThat(fixture.supervisor.getSnapshot()).isEqualTo(before);
    }

    @Test void concurrentCallbacksEnterRuntimeSerially() throws Exception {
        SerialCheckingRuntime runtime = new SerialCheckingRuntime(new DefaultPaperRuntimeEngine());
        Fixture fixture = fixture(runtime);
        fixture.supervisor.start(BASE.plusSeconds(1));
        int count = 12;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                    fixture.supervisor.onBookTicker(book(BASE.plusSeconds(10), "99", "100", "1"));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        ready.await();
        go.countDown();
        done.await();
        assertThat(runtime.maxConcurrent.get()).isOne();
    }

    @Test void invalidEventContextFailsSupervisor() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        StreamBookTickerEvent invalid = book(BASE.plusSeconds(2), "99", "100", "1");
        invalid.setSymbol("ETHUSDT");
        assertThatThrownBy(() -> fixture.supervisor.onBookTicker(invalid))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_CONTEXT_MISMATCH);
        assertThat(fixture.supervisor.getSnapshot().getState()).isEqualTo(PaperSessionSupervisorState.FAILED);
    }

    @Test void failedStopCanCloseWithoutSecondUnsubscribe() {
        Fixture fixture = fixture();
        fixture.supervisor.start(BASE.plusSeconds(1));
        fixture.stream.unsubscribeFailure = new IllegalStateException("close");
        assertThatThrownBy(() -> fixture.supervisor.stop(BASE.plusSeconds(2)))
                .extracting("errorCode").isEqualTo(PaperSessionSupervisorException.PAPER_SUPERVISOR_STREAM_UNSUBSCRIBE_FAILED);
        PaperSessionSupervisorSnapshot stopped = fixture.supervisor.stop(BASE.plusSeconds(3));
        assertThat(stopped.getState()).isEqualTo(PaperSessionSupervisorState.STOPPED);
        assertThat(fixture.stream.unsubscribeCount).isOne();
    }

    @Test void snapshotEqualityIncludesAllStateFields() {
        Fixture fixture = fixture();
        PaperSessionSupervisorSnapshot first = fixture.supervisor.getSnapshot();
        PaperSessionSupervisorSnapshot second = fixture.supervisor.getSnapshot();
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    private Fixture fixture() { return fixture(new DefaultPaperRuntimeEngine(), new DefaultPaperOrderAuditEngine()); }

    private Fixture fixture(PaperRuntimeEngine runtimeEngine) { return fixture(runtimeEngine, new DefaultPaperOrderAuditEngine()); }

    private Fixture fixture(PaperRuntimeEngine runtimeEngine, PaperOrderAuditEngine audit) {
        return fixtureWithSeed(runtimeEngine, audit, candles(100, 100, 100, 100, 100));
    }

    private Fixture orderFixture() {
        return fixtureWithSeed(new DefaultPaperRuntimeEngine(), new DefaultPaperOrderAuditEngine(),
                candles(100, 99, 98, 97, 98));
    }

    private Fixture fixtureWithSeed(PaperRuntimeEngine runtimeEngine, PaperOrderAuditEngine audit,
                                    List<HistoricalCandle> seed) {
        PaperRuntimeSnapshot runtime = runtimeEngine.initialize(config(), seed, account(BASE));
        PaperOrderAuditLedger ledger = ledger(runtime);
        return newSupervisorFixture(runtime, ledger, audit, new CountingStream(), runtimeEngine);
    }

    private Fixture newSupervisorFixture(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger,
                                         PaperOrderAuditEngine audit, CountingStream stream,
                                         PaperRuntimeEngine runtimeEngine) {
        return new Fixture(runtime, ledger, audit, stream,
                newSupervisor(runtime, ledger, audit, stream, runtimeEngine));
    }

    private PaperSessionSupervisor newSupervisor(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger,
                                                 PaperOrderAuditEngine audit, CountingStream stream) {
        return newSupervisor(runtime, ledger, audit, stream, BASE);
    }

    private PaperSessionSupervisor newSupervisor(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger,
                                                 PaperOrderAuditEngine audit, CountingStream stream, Instant initializedAt) {
        return new DefaultPaperSessionSupervisor(stream, new DefaultPaperRuntimeEngine(), audit,
                runtime, ledger, initializedAt);
    }

    private PaperSessionSupervisor newSupervisor(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger,
                                                 PaperOrderAuditEngine audit, CountingStream stream,
                                                 PaperRuntimeEngine runtimeEngine) {
        return new DefaultPaperSessionSupervisor(stream, runtimeEngine, audit, runtime, ledger, BASE);
    }

    private PaperOrderAuditLedger ledger(PaperRuntimeSnapshot runtime) {
        return new PaperOrderAuditLedger(runtime.getTradingSession().getConfig().getSessionId(),
                PROVIDER, MARKET_TYPE, SYMBOL, List.of(), 0, BASE, BASE);
    }

    private PaperRuntimeConfig config() {
        PaperTradingSessionConfig trading = new PaperTradingSessionConfig(
                "runtime-session", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                new MarketOrderQuantityRules(PROVIDER, MARKET_TYPE, SYMBOL, "USDT", 3,
                        new BigDecimal("0.001"), new BigDecimal("0.001"), new BigDecimal("1000"), new BigDecimal("5")),
                BigDecimal.ONE, new PreTradeRiskPolicy(new BigDecimal("0.90"), new BigDecimal("0.90"),
                        new BigDecimal("0.01"), new BigDecimal("0.50"), 5),
                new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", BigDecimal.ZERO));
        return new PaperRuntimeConfig(new RuntimeMarketKey(PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1), 6, trading);
    }

    private PaperAccountSnapshot account(Instant at) {
        PaperAccountEngine engine = new DefaultPaperAccountEngine();
        return engine.initialize("runtime-account", PROVIDER, MARKET_TYPE, "USDT", new BigDecimal("10000"),
                at.atZone(ZoneOffset.UTC).toLocalDate(), at);
    }

    private List<HistoricalCandle> candles(int... closes) {
        List<HistoricalCandle> result = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            BigDecimal price = BigDecimal.valueOf(closes[i]);
            HistoricalCandle candle = new HistoricalCandle();
            candle.setProvider(PROVIDER); candle.setMarketType(MARKET_TYPE); candle.setSymbol(SYMBOL);
            candle.setInterval(KlineInterval.M1); candle.setOpenTime(BASE.plusSeconds(i * 60L));
            candle.setCloseTime(BASE.plusSeconds((i + 1L) * 60L).minusMillis(1));
            candle.setOpenPrice(price); candle.setHighPrice(price); candle.setLowPrice(price); candle.setClosePrice(price);
            candle.setVolume(BigDecimal.TEN); candle.setQuoteVolume(new BigDecimal("1000"));
            candle.setTradeCount(10); candle.setTakerBuyBaseVolume(BigDecimal.ONE);
            candle.setTakerBuyQuoteVolume(new BigDecimal("100")); result.add(candle);
        }
        return result;
    }

    private StreamKlineEvent kline(Instant time, String close, boolean closed) {
        StreamKlineEvent event = new StreamKlineEvent(); event.setProvider(PROVIDER); event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL); event.setInterval(KlineInterval.M1); event.setEventTime(time);
        event.setOpenTime(time.minusSeconds(60)); event.setCloseTime(time.minusMillis(1));
        BigDecimal value = new BigDecimal(close); event.setOpen(value); event.setHigh(value); event.setLow(value);
        event.setClose(value); event.setVolume(BigDecimal.TEN); event.setQuoteVolume(new BigDecimal("1000"));
        event.setTradeCount(10); event.setTakerBuyBaseVolume(BigDecimal.ONE);
        event.setTakerBuyQuoteVolume(new BigDecimal("100")); event.setClosed(closed); return event;
    }

    private StreamBookTickerEvent book(Instant time, String bid, String ask, String quantity) {
        StreamBookTickerEvent event = new StreamBookTickerEvent(); event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE); event.setSymbol(SYMBOL); event.setEventTime(time);
        event.setBidPrice(new BigDecimal(bid)); event.setAskPrice(new BigDecimal(ask));
        event.setBidQuantity(new BigDecimal(quantity)); event.setAskQuantity(new BigDecimal(quantity)); return event;
    }

    private StreamMarkPriceEvent mark(Instant time, String price) {
        StreamMarkPriceEvent event = new StreamMarkPriceEvent(); event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE); event.setSymbol(SYMBOL); event.setEventTime(time);
        event.setMarkPrice(new BigDecimal(price)); event.setIndexPrice(new BigDecimal(price));
        event.setEstimatedSettlePrice(new BigDecimal(price)); event.setLastFundingRate(BigDecimal.ZERO);
        event.setInterestRate(BigDecimal.ZERO); event.setNextFundingTime(time.plusSeconds(3600)); return event;
    }

    private StreamTickerEvent ticker(Instant time) {
        StreamTickerEvent event = new StreamTickerEvent(); event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE); event.setSymbol(SYMBOL); event.setEventTime(time);
        event.setLastPrice(new BigDecimal("100")); return event;
    }

    private StreamStatusEvent status(StreamStatus status, Instant timestamp, String message) {
        StreamStatusEvent event = new StreamStatusEvent(); event.setProvider(PROVIDER); event.setSymbol(SYMBOL);
        event.setInterval(KlineInterval.M1); event.setStatus(status); event.setTimestamp(timestamp); event.setMessage(message); return event;
    }

    private static final class Fixture {
        private final PaperRuntimeSnapshot runtime; private final PaperOrderAuditLedger ledger;
        private final PaperOrderAuditEngine audit; private final CountingStream stream;
        private final PaperSessionSupervisor supervisor;
        private Fixture(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger, PaperOrderAuditEngine audit,
                        CountingStream stream, PaperSessionSupervisor supervisor) {
            this.runtime = runtime; this.ledger = ledger; this.audit = audit; this.stream = stream;
            this.supervisor = supervisor;
        }
    }

    private static class CountingStream implements MarketStreamClient {
        private int subscribeCount; private int unsubscribeCount; private MarketProviderId provider;
        private String symbol; private KlineInterval interval; private RuntimeException subscribeFailure; private RuntimeException unsubscribeFailure;
        @Override public void subscribe(MarketProviderId provider, String symbol, KlineInterval interval, com.aiprovider.quant.market.stream.port.MarketStreamListener listener) {
            subscribeCount++; this.provider = provider; this.symbol = symbol; this.interval = interval; if (subscribeFailure != null) throw subscribeFailure;
        }
        @Override public void unsubscribe(MarketProviderId provider, String symbol, KlineInterval interval, com.aiprovider.quant.market.stream.port.MarketStreamListener listener) {
            unsubscribeCount++; if (unsubscribeFailure != null) throw unsubscribeFailure;
        }
    }

    private static class CountingRuntime implements PaperRuntimeEngine {
        private final PaperRuntimeEngine delegate; private int calls;
        private CountingRuntime(PaperRuntimeEngine delegate) { this.delegate = delegate; }
        @Override public PaperRuntimeSnapshot initialize(PaperRuntimeConfig c, List<HistoricalCandle> s, PaperAccountSnapshot a) { return delegate.initialize(c, s, a); }
        @Override public PaperRuntimeStepResult onKline(PaperRuntimeSnapshot r, StreamKlineEvent e) { calls++; return delegate.onKline(r, e); }
        @Override public PaperRuntimeStepResult onBookTicker(PaperRuntimeSnapshot r, StreamBookTickerEvent e) { calls++; return delegate.onBookTicker(r, e); }
        @Override public PaperRuntimeStepResult onMarkPrice(PaperRuntimeSnapshot r, StreamMarkPriceEvent e) { calls++; return delegate.onMarkPrice(r, e); }
    }

    private static class SerialCheckingRuntime implements PaperRuntimeEngine {
        private final PaperRuntimeEngine delegate;
        private final AtomicInteger active = new AtomicInteger(); private final AtomicInteger maxConcurrent = new AtomicInteger();
        private SerialCheckingRuntime(PaperRuntimeEngine delegate) { this.delegate = delegate; }
        @Override public PaperRuntimeSnapshot initialize(PaperRuntimeConfig c, List<HistoricalCandle> s, PaperAccountSnapshot a) { return delegate.initialize(c, s, a); }
        @Override public PaperRuntimeStepResult onKline(PaperRuntimeSnapshot r, StreamKlineEvent e) { return delegate.onKline(r, e); }
        @Override public PaperRuntimeStepResult onMarkPrice(PaperRuntimeSnapshot r, StreamMarkPriceEvent e) { return delegate.onMarkPrice(r, e); }
        @Override public PaperRuntimeStepResult onBookTicker(PaperRuntimeSnapshot r, StreamBookTickerEvent e) {
            int current = active.incrementAndGet(); maxConcurrent.accumulateAndGet(current, Math::max);
            try { return delegate.onBookTicker(r, e); } finally { active.decrementAndGet(); }
        }
    }

    private static class ThrowingRuntime implements PaperRuntimeEngine {
        private final PaperRuntimeEngine delegate = new DefaultPaperRuntimeEngine();
        @Override public PaperRuntimeSnapshot initialize(PaperRuntimeConfig c, List<HistoricalCandle> s, PaperAccountSnapshot a) { return delegate.initialize(c, s, a); }
        @Override public PaperRuntimeStepResult onKline(PaperRuntimeSnapshot r, StreamKlineEvent e) { throw new IllegalStateException("runtime"); }
        @Override public PaperRuntimeStepResult onBookTicker(PaperRuntimeSnapshot r, StreamBookTickerEvent e) { throw new IllegalStateException("runtime"); }
        @Override public PaperRuntimeStepResult onMarkPrice(PaperRuntimeSnapshot r, StreamMarkPriceEvent e) { throw new IllegalStateException("runtime"); }
    }
    private static class InconsistentInitialAudit implements PaperOrderAuditEngine {
        @Override public PaperOrderAuditLedger initialize(PaperRuntimeSnapshot r, List<ExecutionOrderSnapshot> s, Instant i) { return null; }
        @Override public PaperOrderAuditUpdateResult record(PaperOrderAuditLedger l, PaperRuntimeStepResult r, Instant i) { return null; }
        @Override public PaperReconciliationReport reconcile(PaperOrderAuditLedger l, PaperRuntimeSnapshot r, Instant i) {
            return inconsistentReport(i);
        }
    }
    private static class InconsistentStepAudit implements PaperOrderAuditEngine {
        private final PaperOrderAuditEngine delegate; private int reconcileCalls;
        private InconsistentStepAudit(PaperOrderAuditEngine delegate) { this.delegate = delegate; }
        @Override public PaperOrderAuditLedger initialize(PaperRuntimeSnapshot r, List<ExecutionOrderSnapshot> s, Instant i) { return delegate.initialize(r, s, i); }
        @Override public PaperOrderAuditUpdateResult record(PaperOrderAuditLedger l, PaperRuntimeStepResult r, Instant i) { return delegate.record(l, r, i); }
        @Override public PaperReconciliationReport reconcile(PaperOrderAuditLedger l, PaperRuntimeSnapshot r, Instant i) {
            reconcileCalls++;
            if (reconcileCalls > 1) return inconsistentReport(i);
            return delegate.reconcile(l, r, i);
        }
    }
    private static class CountingAudit implements PaperOrderAuditEngine {
        private final PaperOrderAuditEngine delegate; private int reconcileCalls; private CountingAudit(PaperOrderAuditEngine delegate) { this.delegate = delegate; }
        @Override public PaperOrderAuditLedger initialize(PaperRuntimeSnapshot r, List<ExecutionOrderSnapshot> s, Instant i) { return delegate.initialize(r, s, i); }
        @Override public PaperOrderAuditUpdateResult record(PaperOrderAuditLedger l, PaperRuntimeStepResult r, Instant i) { return delegate.record(l, r, i); }
        @Override public PaperReconciliationReport reconcile(PaperOrderAuditLedger l, PaperRuntimeSnapshot r, Instant i) { reconcileCalls++; return delegate.reconcile(l, r, i); }
    }
    private static class ThrowingAudit implements PaperOrderAuditEngine {
        private final PaperOrderAuditEngine delegate; private ThrowingAudit(PaperOrderAuditEngine delegate) { this.delegate = delegate; }
        @Override public PaperOrderAuditLedger initialize(PaperRuntimeSnapshot r, List<ExecutionOrderSnapshot> s, Instant i) { return delegate.initialize(r, s, i); }
        @Override public PaperOrderAuditUpdateResult record(PaperOrderAuditLedger l, PaperRuntimeStepResult r, Instant i) { throw new IllegalStateException("audit"); }
        @Override public PaperReconciliationReport reconcile(PaperOrderAuditLedger l, PaperRuntimeSnapshot r, Instant i) { return delegate.reconcile(l, r, i); }
    }

    private static PaperReconciliationReport inconsistentReport(Instant reconciledAt) {
        try {
            PaperReconciliationViolation violation = new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.SESSION_TIME_INVALID, "test inconsistency",
                    null, null, null, "expected", "actual");
            var constructor = PaperReconciliationReport.class.getDeclaredConstructor(
                    List.class, int.class, int.class, int.class, BigDecimal.class, String.class,
                    BigDecimal.class, Instant.class, Instant.class);
            constructor.setAccessible(true);
            return constructor.newInstance(List.of(violation), 0, 0, 0, BigDecimal.ZERO,
                    null, BigDecimal.ZERO, null, reconciledAt);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
