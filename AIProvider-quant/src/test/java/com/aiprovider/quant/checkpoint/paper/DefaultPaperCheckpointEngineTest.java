package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.audit.paper.DefaultPaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.audit.paper.PaperOrderAuditUpdateResult;
import com.aiprovider.quant.engine.paper.DefaultPaperTradingEngine;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.DefaultRuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.reconciliation.paper.DefaultPaperReconciliationEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationRequest;
import com.aiprovider.quant.runtime.paper.DefaultPaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeConfig;
import com.aiprovider.quant.runtime.paper.PaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperCheckpointEngineTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");

    private final PaperAccountEngine accountEngine = new DefaultPaperAccountEngine();

    @Test
    void createsValidEmptyPositionCheckpoint() {
        Fixture fixture = fixture();
        PaperRuntimeCheckpoint checkpoint = new DefaultPaperCheckpointEngine()
                .create(fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1));

        assertThat(checkpoint.getSessionId()).isEqualTo("checkpoint-session");
        assertThat(checkpoint.getVersion()).isZero();
        assertThat(checkpoint.getReconciliationReport().getStatus().name())
                .isEqualTo("CONSISTENT");
        assertThat(checkpoint.getCreatedAt()).isEqualTo(BASE.plusSeconds(1));
    }

    @Test
    void createsValidOpenPositionCheckpoint() {
        PaperRuntimeSnapshot runtime = pendingRuntime();
        PaperOrderAuditLedger ledger = new PaperOrderAuditLedger(
                "checkpoint-session", PROVIDER, MARKET_TYPE, SYMBOL,
                List.of(runtime.getTradingSession().getPendingOrderSnapshot()), 0,
                BASE, BASE.plusSeconds(360));
        PaperRuntimeCheckpoint checkpoint = new DefaultPaperCheckpointEngine()
                .create(runtime, ledger, 0, BASE.plusSeconds(360));

        assertThat(checkpoint.getReconciliationReport().getStatus().name())
                .isEqualTo("CONSISTENT");
    }

    @Test
    void rejectsRuntimeAndLedgerSessionMismatch() {
        Fixture fixture = fixture();
        PaperOrderAuditLedger ledger = ledger("other-session", SYMBOL, BASE);
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, ledger, 0, BASE.plusSeconds(1)),
                PaperCheckpointException.PAPER_CHECKPOINT_CONTEXT_MISMATCH);
    }

    @Test
    void rejectsProviderMismatchAsInvalidCheckpointContext() {
        Fixture fixture = fixture();
        assertCode(() -> new PaperRuntimeCheckpoint(
                "checkpoint-session", null, MARKET_TYPE, SYMBOL, KlineInterval.M1, 0,
                fixture.runtime, fixture.ledger, fixture.report(BASE.plusSeconds(1)),
                BASE.plusSeconds(1)), PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID);
    }

    @Test
    void rejectsSymbolMismatch() {
        Fixture fixture = fixture();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, ledger("checkpoint-session", "ETHUSDT", BASE), 0,
                BASE.plusSeconds(1)), PaperCheckpointException.PAPER_CHECKPOINT_CONTEXT_MISMATCH);
    }

    @Test
    void rejectsNegativeVersion() {
        Fixture fixture = fixture();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, -1, BASE.plusSeconds(1)),
                PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID);
    }

    @Test
    void rejectsCreatedAtBeforeSessionTime() {
        Fixture fixture = fixture();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, 0, BASE.minusSeconds(1)),
                PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsCreatedAtBeforeLedgerTime() {
        Fixture fixture = fixture();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, ledger("checkpoint-session", SYMBOL, BASE.plusSeconds(10)),
                0, BASE.plusSeconds(1)), PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsCreatedAtBeforeKlineWatermark() {
        Fixture fixture = fixtureWithKline();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(350)),
                PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsCreatedAtBeforeBookTickerWatermark() {
        Fixture fixture = fixtureWithBook();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(3600)),
                PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsCreatedAtBeforeMarkPriceWatermark() {
        Fixture fixture = fixtureWithMark();
        assertCode(() -> new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(3600)),
                PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsInconsistentCreationAndIncludesEveryViolationCode() {
        PaperRuntimeSnapshot runtime = pendingRuntime();
        PaperOrderAuditLedger ledger = ledger("checkpoint-session", SYMBOL, BASE);
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine(
                new TestAuditEngine((currentLedger, currentRuntime, at) ->
                        new DefaultPaperReconciliationEngine().reconcile(
                                new PaperReconciliationRequest(
                                        currentRuntime.getTradingSession(), List.of(), at))));

        assertThatThrownBy(() -> engine.create(runtime, ledger, 0, BASE.plusSeconds(360)))
                .isInstanceOfSatisfying(PaperCheckpointException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT);
                    assertThat(exception.getMessage()).contains("PENDING_ORDER_MISSING_FROM_HISTORY");
                });
    }

    @Test
    void restoresValidCheckpointAndReconcilesAgain() {
        Fixture fixture = fixture();
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine();
        PaperRuntimeCheckpoint checkpoint = engine.create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1));

        PaperCheckpointRestoreResult result = engine.restore(checkpoint, BASE.plusSeconds(2));

        assertThat(result.getRuntime()).isSameAs(fixture.runtime);
        assertThat(result.getLedger()).isSameAs(fixture.ledger);
        assertThat(result.getCheckpointVersion()).isZero();
        assertThat(result.getCheckpointCreatedAt()).isEqualTo(BASE.plusSeconds(1));
        assertThat(result.getRestoredAt()).isEqualTo(BASE.plusSeconds(2));
        assertThat(result.getReconciliationReport().getStatus().name()).isEqualTo("CONSISTENT");
    }

    @Test
    void rejectsRestoreBeforeCheckpointCreation() {
        Fixture fixture = fixture();
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine();
        PaperRuntimeCheckpoint checkpoint = engine.create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(2));
        assertCode(() -> engine.restore(checkpoint, BASE.plusSeconds(1)),
                PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID);
    }

    @Test
    void rejectsInconsistentRestore() {
        Fixture fixture = fixture();
        DefaultPaperCheckpointEngine creator = new DefaultPaperCheckpointEngine();
        PaperRuntimeCheckpoint checkpoint = creator.create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1));
        DefaultPaperCheckpointEngine restoring = new DefaultPaperCheckpointEngine(
                new TestAuditEngine((ledger, runtime, at) ->
                        new DefaultPaperReconciliationEngine().reconcile(
                                new PaperReconciliationRequest(runtime.getTradingSession(),
                                        List.of(runtime.getTradingSession().getLastOrderSnapshot()), at))));

        assertCode(() -> restoring.restore(checkpoint, BASE.plusSeconds(2)),
                PaperCheckpointException.PAPER_CHECKPOINT_RESTORE_INCONSISTENT);
    }

    @Test
    void preservesAuditCauseDuringCreationFailure() {
        RuntimeException cause = new IllegalStateException("audit failure");
        Fixture fixture = fixture();
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine(
                new TestAuditEngine((ledger, runtime, at) -> { throw cause; }));
        assertThatThrownBy(() -> engine.create(fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1)))
                .isInstanceOfSatisfying(PaperCheckpointException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void preservesAuditCauseDuringRestoreFailure() {
        Fixture fixture = fixture();
        PaperRuntimeCheckpoint checkpoint = new DefaultPaperCheckpointEngine().create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1));
        RuntimeException cause = new IllegalStateException("restore audit failure");
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine(
                new TestAuditEngine((ledger, runtime, at) -> { throw cause; }));
        assertThatThrownBy(() -> engine.restore(checkpoint, BASE.plusSeconds(2)))
                .isInstanceOfSatisfying(PaperCheckpointException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(PaperCheckpointException.PAPER_CHECKPOINT_RESTORE_INCONSISTENT);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void checkpointAndRestoreResultsAreImmutableSnapshots() {
        Fixture fixture = fixture();
        DefaultPaperCheckpointEngine engine = new DefaultPaperCheckpointEngine();
        PaperRuntimeCheckpoint checkpoint = engine.create(
                fixture.runtime, fixture.ledger, 0, BASE.plusSeconds(1));
        PaperCheckpointRestoreResult result = engine.restore(checkpoint, BASE.plusSeconds(2));

        assertThat(checkpoint.getCreatedAt()).isNotSameAs(checkpoint.getCreatedAt());
        assertThat(result.getRestoredAt()).isNotSameAs(result.getRestoredAt());
        assertThat(result.getRuntime()).isSameAs(checkpoint.getRuntime());
    }

    private Fixture fixture() {
        PaperRuntimeEngine runtimeEngine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot runtime = runtimeEngine.initialize(
                config("checkpoint-session"), candles(100, 100, 100, 100, 100), account(BASE));
        PaperOrderAuditLedger ledger = ledger("checkpoint-session", SYMBOL, BASE);
        PaperReconciliationReport report = new DefaultPaperOrderAuditEngine()
                .reconcile(ledger, runtime, BASE.plusSeconds(1));
        return new Fixture(runtime, ledger, report);
    }

    private Fixture fixtureWithKline() {
        Fixture fixture = fixture();
        StreamKlineEvent event = kline(BASE.plusSeconds(360));
        PaperRuntimeSnapshot runtime = new DefaultPaperRuntimeEngine()
                .onKline(fixture.runtime, event).getRuntime();
        PaperOrderAuditLedger ledger = ledger("checkpoint-session", SYMBOL, BASE);
        return new Fixture(runtime, ledger, fixture.report(BASE.plusSeconds(3601)));
    }

    private Fixture fixtureWithBook() {
        Fixture fixture = fixture();
        PaperRuntimeSnapshot runtime = new DefaultPaperRuntimeEngine()
                .onBookTicker(fixture.runtime, book(BASE.plusSeconds(3601))).getRuntime();
        return new Fixture(runtime, fixture.ledger, fixture.report(BASE.plusSeconds(3601)));
    }

    private Fixture fixtureWithMark() {
        Fixture fixture = fixture();
        PaperRuntimeSnapshot runtime = new DefaultPaperRuntimeEngine()
                .onMarkPrice(fixture.runtime, mark(BASE.plusSeconds(3601))).getRuntime();
        return new Fixture(runtime, fixture.ledger, fixture.report(BASE.plusSeconds(3601)));
    }

    private PaperRuntimeSnapshot pendingRuntime() {
        PaperRuntimeEngine engine = new DefaultPaperRuntimeEngine();
        PaperRuntimeSnapshot initial = engine.initialize(
                config("checkpoint-session"), candles(100, 99, 98, 97, 98), account(BASE));
        PaperRuntimeSnapshot booked = engine.onBookTicker(
                initial, book(BASE.plusSeconds(299))).getRuntime();
        StreamKlineEvent event = kline(BASE.plusSeconds(360));
        event.setClose(new BigDecimal("99"));
        return engine.onKline(booked, event).getRuntime();
    }

    private PaperOrderAuditLedger ledger(String sessionId, String symbol, Instant updatedAt) {
        return new PaperOrderAuditLedger(
                sessionId, PROVIDER, MARKET_TYPE, symbol, List.of(), 0, BASE, updatedAt);
    }

    private PaperReconciliationReport report(Instant at) {
        return new DefaultPaperOrderAuditEngine().reconcile(
                fixtureLedger(), fixtureRuntime(), at);
    }

    private PaperOrderAuditLedger fixtureLedger() { return ledger("checkpoint-session", SYMBOL, BASE); }

    private PaperRuntimeSnapshot fixtureRuntime() {
        return new DefaultPaperRuntimeEngine().initialize(
                config("checkpoint-session"), candles(100, 100, 100, 100, 100), account(BASE));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PaperCheckpointException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private PaperRuntimeConfig config(String sessionId) {
        return new PaperRuntimeConfig(
                new RuntimeMarketKey(PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1), 6,
                new com.aiprovider.quant.engine.paper.PaperTradingSessionConfig(
                        sessionId, PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                        "EMA_CROSS_LONG_ONLY", "1.0.0", java.util.Map.of("fastPeriod", 2,
                        "slowPeriod", 4),
                        com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType
                                .FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                        new com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules(
                                PROVIDER, MARKET_TYPE, SYMBOL, "USDT", 3,
                                new BigDecimal("0.001"), new BigDecimal("0.001"),
                                new BigDecimal("1000"), new BigDecimal("5")), BigDecimal.ONE,
                        new com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy(
                                new BigDecimal("0.90"), new BigDecimal("0.90"),
                                new BigDecimal("0.01"), new BigDecimal("0.50"), 5),
                        new com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy(
                                new BigDecimal("0.001"), "USDT", BigDecimal.ZERO)));
    }

    private PaperAccountSnapshot account(Instant initializedAt) {
        return accountEngine.initialize("checkpoint-account", PROVIDER, MARKET_TYPE, "USDT",
                new BigDecimal("10000"), initializedAt.atZone(ZoneOffset.UTC).toLocalDate(),
                initializedAt);
    }

    private List<HistoricalCandle> candles(int... closes) {
        List<HistoricalCandle> result = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            BigDecimal price = new BigDecimal(closes[index]);
            HistoricalCandle candle = new HistoricalCandle();
            candle.setProvider(PROVIDER);
            candle.setMarketType(MARKET_TYPE);
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
            result.add(candle);
        }
        return result;
    }

    private StreamKlineEvent kline(Instant eventTime) {
        StreamKlineEvent event = new StreamKlineEvent();
        event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL);
        event.setInterval(KlineInterval.M1);
        event.setEventTime(eventTime);
        event.setOpenTime(eventTime.minusSeconds(60));
        event.setCloseTime(eventTime.minusMillis(1));
        event.setOpen(new BigDecimal("100"));
        event.setHigh(new BigDecimal("100"));
        event.setLow(new BigDecimal("99"));
        event.setClose(new BigDecimal("100"));
        event.setVolume(BigDecimal.TEN);
        event.setQuoteVolume(new BigDecimal("1000"));
        event.setTradeCount(10);
        event.setTakerBuyBaseVolume(BigDecimal.ONE);
        event.setTakerBuyQuoteVolume(new BigDecimal("100"));
        event.setClosed(true);
        return event;
    }

    private StreamBookTickerEvent book(Instant eventTime) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL);
        event.setEventTime(eventTime);
        event.setBidPrice(new BigDecimal("98"));
        event.setBidQuantity(BigDecimal.ONE);
        event.setAskPrice(new BigDecimal("99"));
        event.setAskQuantity(BigDecimal.ONE);
        return event;
    }

    private StreamMarkPriceEvent mark(Instant eventTime) {
        StreamMarkPriceEvent event = new StreamMarkPriceEvent();
        event.setProvider(PROVIDER);
        event.setMarketType(MARKET_TYPE);
        event.setSymbol(SYMBOL);
        event.setEventTime(eventTime);
        event.setMarkPrice(new BigDecimal("101"));
        event.setIndexPrice(new BigDecimal("101"));
        event.setEstimatedSettlePrice(new BigDecimal("101"));
        event.setLastFundingRate(new BigDecimal("-0.0001"));
        event.setInterestRate(new BigDecimal("0.0001"));
        event.setNextFundingTime(eventTime.plusSeconds(3600));
        return event;
    }

    private static final class Fixture {
        private final PaperRuntimeSnapshot runtime;
        private final PaperOrderAuditLedger ledger;
        private final PaperReconciliationReport report;

        private Fixture(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger,
                        PaperReconciliationReport report) {
            this.runtime = runtime;
            this.ledger = ledger;
            this.report = report;
        }

        private PaperReconciliationReport report(Instant at) {
            return new DefaultPaperOrderAuditEngine().reconcile(ledger, runtime, at);
        }
    }

    @FunctionalInterface
    private interface ReconcileFunction {
        PaperReconciliationReport apply(
                PaperOrderAuditLedger ledger, PaperRuntimeSnapshot runtime, Instant at);
    }

    private static final class TestAuditEngine implements PaperOrderAuditEngine {
        private final ReconcileFunction reconcileFunction;

        private TestAuditEngine(ReconcileFunction reconcileFunction) {
            this.reconcileFunction = reconcileFunction;
        }

        @Override
        public PaperOrderAuditLedger initialize(
                PaperRuntimeSnapshot runtime, List<ExecutionOrderSnapshot> seedOrderHistory,
                Instant initializedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaperOrderAuditUpdateResult record(
                PaperOrderAuditLedger ledger, PaperRuntimeStepResult runtimeStepResult,
                Instant recordedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PaperReconciliationReport reconcile(
                PaperOrderAuditLedger ledger, PaperRuntimeSnapshot runtime, Instant reconciledAt) {
            return reconcileFunction.apply(ledger, runtime, reconciledAt);
        }
    }
}
