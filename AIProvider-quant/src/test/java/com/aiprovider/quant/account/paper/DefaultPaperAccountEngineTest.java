package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperAccountEngineTest {
    private static final Instant T0 = Instant.parse("2026-07-28T00:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 28);
    private static final String SYMBOL = "BTCUSDT";

    private final PaperAccountEngine engine = new DefaultPaperAccountEngine();

    @Test
    void initializesFlatAccount() {
        PaperAccountSnapshot account = account();

        assertThat(account.getAccountId()).isEqualTo("paper-1");
        assertThat(account.getProvider()).isEqualTo(MarketProviderId.BINANCE_USDM);
        assertThat(account.getMarketType()).isEqualTo(MarketType.USDM_PERPETUAL);
        assertThat(account.getQuoteAsset()).isEqualTo("USDT");
        assertThat(account.getInitialCapital()).isEqualByComparingTo("10000");
        assertThat(account.getRealizedPnl()).isEqualByComparingTo("0");
        assertThat(account.getUnrealizedPnl()).isEqualByComparingTo("0");
        assertThat(account.getTotalEquity()).isEqualByComparingTo("10000");
        assertThat(account.getAvailableCapital()).isEqualByComparingTo("10000");
        assertThat(account.getPosition().isFlat()).isTrue();
        assertThat(account.getPosition().getQuantity()).isEqualByComparingTo("0");
        assertThat(account.getPosition().getPositionNotional()).isEqualByComparingTo("0");
        assertThat(account.getPosition().getUnrealizedPnl()).isEqualByComparingTo("0");
        assertThat(account.getPosition().getOpenTradeNetPnl()).isEqualByComparingTo("0");
        assertThat(account.getTradingDayState().getUtcDate()).isEqualTo(DAY);
        assertThat(account.getTradingDayState().getDayStartEquity()).isEqualByComparingTo("10000");
        assertThat(account.getTradingDayState().getDailyRealizedPnl()).isEqualByComparingTo("0");
        assertThat(account.getConsecutiveLosses()).isZero();
        assertThat(account.getAppliedFills()).isEmpty();
        assertThat(account.getLastUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void appliesSingleBuyToCapitalPositionAndFees() {
        PaperAccountSnapshot original = account();
        PaperAccountUpdateResult result = engine.applyFill(
                original,
                buy("open-1", "2", T0),
                fill("fill-1", "2", "100", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot account = result.getAccount();

        assertThat(result.isApplied()).isTrue();
        assertThat(original.getPosition().isFlat()).isTrue();
        assertThat(account.getPosition().isOpen()).isTrue();
        assertThat(account.getPosition().getSymbol()).isEqualTo(SYMBOL);
        assertThat(account.getPosition().getQuantity()).isEqualByComparingTo("2");
        assertThat(account.getPosition().getAverageEntryPrice()).isEqualByComparingTo("100");
        assertThat(account.getPosition().getMarkPrice()).isEqualByComparingTo("100");
        assertThat(account.getPosition().getPositionNotional()).isEqualByComparingTo("200");
        assertThat(account.getPosition().getUnrealizedPnl()).isEqualByComparingTo("0");
        assertThat(account.getPosition().getOpeningClientOrderId()).isEqualTo("open-1");
        assertThat(account.getPosition().getOpenTradeNetPnl()).isEqualByComparingTo("-1");
        assertThat(account.getRealizedPnl()).isEqualByComparingTo("-1");
        assertThat(account.getTotalEquity()).isEqualByComparingTo("9999");
        assertThat(account.getAvailableCapital()).isEqualByComparingTo("9799");
        assertThat(account.getTradingDayState().getDailyRealizedPnl()).isEqualByComparingTo("-1");
        assertThat(account.getAppliedFills()).hasSize(1);
    }

    @Test
    void calculatesQuantityWeightedAverageAcrossOpeningPartialFills() {
        PaperAccountSnapshot first = applied(
                account(),
                buy("open-1", "4", T0),
                fill("fill-1", "1", "100", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot second = applied(
                first,
                buy("open-1", "4", T0),
                fill("fill-2", "3", "120", "1", T0.plusSeconds(2)));

        assertThat(second.getPosition().getQuantity()).isEqualByComparingTo("4");
        assertThat(second.getPosition().getAverageEntryPrice()).isEqualByComparingTo("115");
        assertThat(second.getPosition().getMarkPrice()).isEqualByComparingTo("120");
        assertThat(second.getPosition().getUnrealizedPnl()).isEqualByComparingTo("20");
        assertThat(second.getPosition().getOpenTradeNetPnl()).isEqualByComparingTo("-2");
        assertThat(second.getAppliedFills()).hasSize(2);
    }

    @Test
    void rejectsAddingToPositionFromDifferentOpeningOrder() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "1", T0),
                fill("fill-1", "1", "100", "0", T0.plusSeconds(1)));

        assertError(
                () -> engine.applyFill(
                        open,
                        buy("open-2", "1", T0.plusSeconds(1)),
                        fill("fill-2", "1", "100", "0", T0.plusSeconds(2))),
                PaperAccountException.PAPER_ACCOUNT_POSITION_ALREADY_OPEN);
    }

    @Test
    void profitableFullCloseUpdatesEquityAndResetsPosition() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "2", T0),
                fill("fill-1", "2", "100", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot closed = applied(
                open,
                sell("close-1", "2", T0.plusSeconds(1)),
                fill("fill-2", "2", "110", "1", T0.plusSeconds(2)));

        assertThat(closed.getRealizedPnl()).isEqualByComparingTo("18");
        assertThat(closed.getUnrealizedPnl()).isEqualByComparingTo("0");
        assertThat(closed.getTotalEquity()).isEqualByComparingTo("10018");
        assertThat(closed.getAvailableCapital()).isEqualByComparingTo("10018");
        assertThat(closed.getPosition().isFlat()).isTrue();
        assertThat(closed.getPosition().getOpeningClientOrderId()).isNull();
        assertThat(closed.getPosition().getOpenTradeNetPnl()).isEqualByComparingTo("0");
        assertThat(closed.getConsecutiveLosses()).isZero();
    }

    @Test
    void losingTradeIncrementsConsecutiveLosses() {
        PaperAccountSnapshot closed = closeTrade(account(), "loss", "100", "90");

        assertThat(closed.getRealizedPnl()).isEqualByComparingTo("-12");
        assertThat(closed.getConsecutiveLosses()).isEqualTo(1);
    }

    @Test
    void profitableTradeResetsConsecutiveLosses() {
        PaperAccountSnapshot loss = closeTrade(account(), "loss", "100", "90");
        PaperAccountSnapshot win = closeTrade(loss, "win", "100", "120");

        assertThat(loss.getConsecutiveLosses()).isEqualTo(1);
        assertThat(win.getConsecutiveLosses()).isZero();
    }

    @Test
    void appliesLegalPartialCloseAndKeepsEntryPrice() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "4", T0),
                fill("fill-1", "4", "100", "0.4", T0.plusSeconds(1)));
        PaperAccountSnapshot partial = applied(
                open,
                sell("close-1", "1", T0.plusSeconds(1)),
                fill("fill-2", "1", "110", "0.1", T0.plusSeconds(2)));

        assertThat(partial.getPosition().isOpen()).isTrue();
        assertThat(partial.getPosition().getQuantity()).isEqualByComparingTo("3");
        assertThat(partial.getPosition().getAverageEntryPrice()).isEqualByComparingTo("100");
        assertThat(partial.getPosition().getMarkPrice()).isEqualByComparingTo("110");
        assertThat(partial.getPosition().getUnrealizedPnl()).isEqualByComparingTo("30");
        assertThat(partial.getPosition().getOpenTradeNetPnl()).isEqualByComparingTo("9.5");
        assertThat(partial.getRealizedPnl()).isEqualByComparingTo("9.5");
        assertThat(partial.getConsecutiveLosses()).isZero();
    }

    @Test
    void rejectsExitQuantityAbovePosition() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "1", T0),
                fill("fill-1", "1", "100", "0", T0.plusSeconds(1)));

        assertError(
                () -> engine.applyFill(
                        open,
                        sell("close-1", "2", T0.plusSeconds(1)),
                        fill("fill-2", "2", "100", "0", T0.plusSeconds(2))),
                PaperAccountException.PAPER_ACCOUNT_EXIT_QUANTITY_EXCEEDED);
    }

    @Test
    void markToMarketCalculatesUnrealizedPnlAndCapital() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "2", T0),
                fill("fill-1", "2", "100", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot marked = engine.markToMarket(
                open, SYMBOL, new BigDecimal("120"), T0.plusSeconds(2));

        assertThat(marked.getPosition().getMarkPrice()).isEqualByComparingTo("120");
        assertThat(marked.getPosition().getPositionNotional()).isEqualByComparingTo("240");
        assertThat(marked.getUnrealizedPnl()).isEqualByComparingTo("40");
        assertThat(marked.getTotalEquity()).isEqualByComparingTo("10039");
        assertThat(marked.getAvailableCapital()).isEqualByComparingTo("9799");
        assertThat(marked.getDailyPnl()).isEqualByComparingTo("39");
    }

    @Test
    void rollsUtcDayAndResetsDailyRealizedPnl() {
        PaperAccountSnapshot open = applied(
                account(),
                buy("open-1", "1", T0),
                fill("fill-1", "1", "100", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot rolled = engine.rollUtcTradingDay(
                open, DAY.plusDays(1), T0.plusSeconds(2));

        assertThat(rolled.getTradingDayState().getUtcDate()).isEqualTo(DAY.plusDays(1));
        assertThat(rolled.getTradingDayState().getDayStartEquity())
                .isEqualByComparingTo(open.getTotalEquity());
        assertThat(rolled.getTradingDayState().getDailyRealizedPnl()).isEqualByComparingTo("0");
        assertThat(rolled.getDailyPnl()).isEqualByComparingTo("0");
    }

    @Test
    void identicalDuplicateFillIsNotAppliedTwice() {
        ExecutionOrderRequest order = buy("open-1", "1", T0);
        ExecutionFill fill = fill("fill-1", "1", "100", "1", T0.plusSeconds(1));
        PaperAccountSnapshot once = applied(account(), order, fill);
        PaperAccountUpdateResult duplicate = engine.applyFill(once, order, fill);

        assertThat(duplicate.isApplied()).isFalse();
        assertThat(duplicate.getAccount()).isSameAs(once);
        assertThat(duplicate.getAccount().getAppliedFills()).hasSize(1);
        assertThat(duplicate.getAccount().getRealizedPnl()).isEqualByComparingTo("-1");
    }

    @Test
    void conflictingDuplicateFillFails() {
        ExecutionOrderRequest order = buy("open-1", "1", T0);
        PaperAccountSnapshot once = applied(
                account(), order, fill("fill-1", "1", "100", "1", T0.plusSeconds(1)));

        assertError(
                () -> engine.applyFill(
                        once, order, fill("fill-1", "1", "101", "1", T0.plusSeconds(1))),
                PaperAccountException.PAPER_ACCOUNT_DUPLICATE_FILL_CONFLICT);

        ExecutionFill changedAsset = new ExecutionFill(
                "fill-1",
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ONE,
                "BNB",
                T0.plusSeconds(1));
        assertError(
                () -> engine.applyFill(once, order, changedAsset),
                PaperAccountException.PAPER_ACCOUNT_DUPLICATE_FILL_CONFLICT);
    }

    @Test
    void rejectsFeeAssetMismatch() {
        ExecutionFill fill = new ExecutionFill(
                "fill-1",
                BigDecimal.ONE,
                new BigDecimal("100"),
                new BigDecimal("0.1"),
                "BNB",
                T0.plusSeconds(1));

        assertError(
                () -> engine.applyFill(account(), buy("open-1", "1", T0), fill),
                PaperAccountException.PAPER_ACCOUNT_FEE_ASSET_MISMATCH);
    }

    @Test
    void identicalInputsProduceIdenticalSnapshots() {
        PaperAccountEngine firstEngine = new DefaultPaperAccountEngine();
        PaperAccountEngine secondEngine = new DefaultPaperAccountEngine();
        ExecutionOrderRequest order = buy("open-1", "3", T0);
        ExecutionFill fill = fill("fill-1", "3", "123.45", "0.37", T0.plusSeconds(1));

        PaperAccountSnapshot first = firstEngine.applyFill(account(firstEngine), order, fill).getAccount();
        PaperAccountSnapshot second = secondEngine.applyFill(account(secondEngine), order, fill).getAccount();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void rejectsInsufficientCapitalWithoutChangingAccount() {
        PaperAccountSnapshot account = account();

        assertError(
                () -> engine.applyFill(
                        account,
                        buy("open-1", "100", T0),
                        fill("fill-1", "100", "100", "1", T0.plusSeconds(1))),
                PaperAccountException.PAPER_ACCOUNT_CAPITAL_INSUFFICIENT);
        assertThat(account.getPosition().isFlat()).isTrue();
        assertThat(account.getAppliedFills()).isEmpty();
    }

    @Test
    void rejectsTimeRegressionForFillMarkAndDayRoll() {
        PaperAccountSnapshot account = account();

        assertError(
                () -> engine.applyFill(
                        account,
                        buy("open-1", "1", T0),
                        fill("fill-1", "1", "100", "0", T0.minusSeconds(1))),
                PaperAccountException.PAPER_ACCOUNT_TIME_INVALID);
        assertError(
                () -> engine.markToMarket(
                        account, SYMBOL, new BigDecimal("100"), T0.minusSeconds(1)),
                PaperAccountException.PAPER_ACCOUNT_TIME_INVALID);
        assertError(
                () -> engine.rollUtcTradingDay(
                        account, DAY.plusDays(1), T0.minusSeconds(1)),
                PaperAccountException.PAPER_ACCOUNT_TIME_INVALID);
    }

    private PaperAccountSnapshot account() {
        return account(engine);
    }

    private PaperAccountSnapshot account(PaperAccountEngine accountEngine) {
        return accountEngine.initialize(
                "paper-1",
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                "USDT",
                new BigDecimal("10000"),
                DAY,
                T0);
    }

    private PaperAccountSnapshot closeTrade(
            PaperAccountSnapshot account, String id, String entryPrice, String exitPrice) {
        Instant openedAt = account.getLastUpdatedAt().plusSeconds(1);
        PaperAccountSnapshot open = applied(
                account,
                buy(id + "-open", "1", account.getLastUpdatedAt()),
                fill(id + "-entry", "1", entryPrice, "1", openedAt));
        return applied(
                open,
                sell(id + "-close", "1", openedAt),
                fill(id + "-exit", "1", exitPrice, "1", openedAt.plusSeconds(1)));
    }

    private PaperAccountSnapshot applied(
            PaperAccountSnapshot account, ExecutionOrderRequest order, ExecutionFill fill) {
        return engine.applyFill(account, order, fill).getAccount();
    }

    private ExecutionOrderRequest buy(
            String clientOrderId, String quantity, Instant requestedAt) {
        return order(clientOrderId, quantity, requestedAt, OrderSide.BUY, false);
    }

    private ExecutionOrderRequest sell(
            String clientOrderId, String quantity, Instant requestedAt) {
        return order(clientOrderId, quantity, requestedAt, OrderSide.SELL, true);
    }

    private ExecutionOrderRequest order(
            String clientOrderId,
            String quantity,
            Instant requestedAt,
            OrderSide side,
            boolean reduceOnly) {
        return new ExecutionOrderRequest(
                clientOrderId,
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                SYMBOL,
                ExecutionOrderType.MARKET,
                side,
                PositionSide.LONG,
                new BigDecimal(quantity),
                reduceOnly,
                requestedAt);
    }

    private ExecutionFill fill(
            String fillId,
            String quantity,
            String price,
            String fee,
            Instant filledAt) {
        return new ExecutionFill(
                fillId,
                new BigDecimal(quantity),
                new BigDecimal(price),
                new BigDecimal(fee),
                "USDT",
                filledAt);
    }

    private void assertError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(PaperAccountException.class)
                .extracting(error -> ((PaperAccountException) error).getErrorCode())
                .isEqualTo(errorCode);
    }
}
