package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.audit.paper.DefaultPaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.runtime.RuntimeMarketKey;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import com.aiprovider.quant.runtime.paper.DefaultPaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeConfig;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPaperCheckpointStoreTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant BASE = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void firstVersionZeroSaveSucceeds() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();

        PaperCheckpointSaveResult result = store.save(fixture.checkpoint(0, 1), OptionalLong.empty());

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getPreviousVersion()).isNull();
        assertThat(result.getCurrentVersion()).isZero();
    }

    @Test
    void firstSaveWithNonZeroVersionConflicts() {
        Fixture fixture = fixture("store-session");
        assertConflict(() -> new InMemoryPaperCheckpointStore().save(
                fixture.checkpoint(1, 1), OptionalLong.empty()));
    }

    @Test
    void firstSaveWithDeclaredPreviousVersionConflicts() {
        Fixture fixture = fixture("store-session");
        assertConflict(() -> new InMemoryPaperCheckpointStore().save(
                fixture.checkpoint(0, 1), OptionalLong.of(0)));
    }

    @Test
    void normalZeroToOneUpdateSucceeds() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        store.save(fixture.checkpoint(0, 1), OptionalLong.empty());

        PaperCheckpointSaveResult result = store.save(
                fixture.checkpoint(1, 2), OptionalLong.of(0));

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getPreviousVersion()).isEqualTo(0L);
        assertThat(result.getCurrentVersion()).isEqualTo(1L);
    }

    @Test
    void wrongExpectedPreviousVersionConflicts() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        store.save(fixture.checkpoint(0, 1), OptionalLong.empty());
        assertConflict(() -> store.save(fixture.checkpoint(1, 2), OptionalLong.of(7)));
    }

    @Test
    void jumpedVersionConflicts() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        store.save(fixture.checkpoint(0, 1), OptionalLong.empty());
        assertConflict(() -> store.save(fixture.checkpoint(2, 2), OptionalLong.of(0)));
    }

    @Test
    void sameVersionSameContentIsIdempotent() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        PaperRuntimeCheckpoint checkpoint = fixture.checkpoint(0, 1);
        store.save(checkpoint, OptionalLong.empty());

        PaperCheckpointSaveResult result = store.save(checkpoint, OptionalLong.of(99));

        assertThat(result.isApplied()).isFalse();
        assertThat(result.getCheckpoint()).isEqualTo(checkpoint);
        assertThat(result.getPreviousVersion()).isZero();
        assertThat(result.getCurrentVersion()).isZero();
    }

    @Test
    void sameVersionDifferentContentConflicts() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        store.save(fixture.checkpoint(0, 1), OptionalLong.empty());
        assertConflict(() -> store.save(fixture.checkpoint(0, 2), OptionalLong.empty()));
    }

    @Test
    void differentSessionsHaveIndependentVersions() {
        Fixture first = fixture("first-session");
        Fixture second = fixture("second-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();

        PaperCheckpointSaveResult firstResult = store.save(
                first.checkpoint(0, 1), OptionalLong.empty());
        PaperCheckpointSaveResult secondResult = store.save(
                second.checkpoint(0, 1), OptionalLong.empty());

        assertThat(firstResult.getCurrentVersion()).isZero();
        assertThat(secondResult.getCurrentVersion()).isZero();
    }

    @Test
    void loadLatestReturnsEmptyForUnknownSessionAndLatestCheckpointForKnownSession() {
        Fixture fixture = fixture("store-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        assertThat(store.loadLatest("missing")).isEmpty();
        PaperRuntimeCheckpoint first = fixture.checkpoint(0, 1);
        PaperRuntimeCheckpoint latest = fixture.checkpoint(1, 2);
        store.save(first, OptionalLong.empty());
        store.save(latest, OptionalLong.of(0));

        assertThat(store.loadLatest("store-session")).contains(latest);
    }

    @Test
    void concurrentCompetitionHasOneAppliedWrite() throws Exception {
        Fixture fixture = fixture("concurrent-session");
        InMemoryPaperCheckpointStore store = new InMemoryPaperCheckpointStore();
        store.save(fixture.checkpoint(0, 1), OptionalLong.empty());
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                final int offset = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        PaperCheckpointSaveResult result = store.save(
                                fixture.checkpoint(1, 2 + offset), OptionalLong.of(0));
                        if (result.isApplied()) {
                            applied.incrementAndGet();
                        }
                    } catch (PaperCheckpointException exception) {
                        if (PaperCheckpointException.PAPER_CHECKPOINT_VERSION_CONFLICT
                                .equals(exception.getErrorCode())) {
                            conflicts.incrementAndGet();
                        } else {
                            throw exception;
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
        assertThat(applied).hasValue(1);
        assertThat(conflicts).hasValue(workers - 1);
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PaperCheckpointException.class)
                .extracting("errorCode")
                .isEqualTo(PaperCheckpointException.PAPER_CHECKPOINT_VERSION_CONFLICT);
    }

    private Fixture fixture(String sessionId) {
        PaperRuntimeSnapshot runtime = new DefaultPaperRuntimeEngine().initialize(
                config(sessionId), candles(), account(BASE));
        PaperOrderAuditLedger ledger = new PaperOrderAuditLedger(
                sessionId, PROVIDER, MARKET_TYPE, SYMBOL, List.of(), 0, BASE, BASE);
        return new Fixture(runtime, ledger);
    }

    private PaperRuntimeConfig config(String sessionId) {
        PaperTradingSessionConfig trading = new PaperTradingSessionConfig(
                sessionId, PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                new MarketOrderQuantityRules(PROVIDER, MARKET_TYPE, SYMBOL, "USDT", 3,
                        new BigDecimal("0.001"), new BigDecimal("0.001"),
                        new BigDecimal("1000"), new BigDecimal("5")), BigDecimal.ONE,
                new PreTradeRiskPolicy(new BigDecimal("0.90"), new BigDecimal("0.90"),
                        new BigDecimal("0.01"), new BigDecimal("0.50"), 5),
                new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", BigDecimal.ZERO));
        return new PaperRuntimeConfig(
                new RuntimeMarketKey(PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1), 6, trading);
    }

    private PaperAccountSnapshot account(Instant initializedAt) {
        return new DefaultPaperAccountEngine().initialize(
                "store-account", PROVIDER, MARKET_TYPE, "USDT", new BigDecimal("10000"),
                initializedAt.atZone(ZoneOffset.UTC).toLocalDate(), initializedAt);
    }

    private List<HistoricalCandle> candles() {
        List<HistoricalCandle> result = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            BigDecimal price = new BigDecimal("100");
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

    private static final class Fixture {
        private final PaperRuntimeSnapshot runtime;
        private final PaperOrderAuditLedger ledger;

        private Fixture(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger) {
            this.runtime = runtime;
            this.ledger = ledger;
        }

        private PaperRuntimeCheckpoint checkpoint(long version, long seconds) {
            return new DefaultPaperCheckpointEngine().create(
                    runtime, ledger, version, BASE.plusSeconds(seconds));
        }
    }
}
