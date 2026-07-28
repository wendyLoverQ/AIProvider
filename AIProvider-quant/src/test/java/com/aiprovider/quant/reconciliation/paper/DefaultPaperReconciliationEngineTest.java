package com.aiprovider.quant.reconciliation.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.account.paper.PaperAccountSnapshotFixture;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshotFixture;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperReconciliationEngineTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final String FEE_ASSET = "USDT";
    private static final Instant T0 = Instant.parse("2026-07-28T00:00:00Z");
    private static final Instant RECONCILED_AT = Instant.parse("2026-07-28T01:00:00Z");

    private final ExecutionOrderStateMachine orderMachine = new ExecutionOrderStateMachine();
    private final PaperAccountEngine accountEngine = new DefaultPaperAccountEngine();
    private final PaperReconciliationEngine engine = new DefaultPaperReconciliationEngine();

    @Test
    void emptyHistoryFlatPositionAndNoAppliedFillsAreConsistent() {
        PaperAccountSnapshot account = account();

        PaperReconciliationReport report =
                reconcile(session(account, null, null), List.of());

        assertConsistent(report, 0, 0, 0, "0", null, "0", null);
    }

    @Test
    void completeBuyIsConsistent() {
        ExecutionOrderRequest request = buy("buy-1", "2", T0);
        ExecutionFill fill = fill("fill-1", "2", "100", "0.2", T0.plusSeconds(3));
        ExecutionOrderSnapshot order = filledOrder(request, "exec-1", fill);
        PaperAccountSnapshot account = applied(account(), request, fill);

        PaperReconciliationReport report =
                reconcile(session(account, null, order), List.of(order));

        assertConsistent(report, 1, 1, 1, "2", "buy-1", "100", fill.getFilledAt());
    }

    @Test
    void partiallyFilledBuyWithPendingOrderIsConsistent() {
        ExecutionOrderRequest request = buy("buy-1", "2", T0);
        ExecutionFill fill = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionOrderSnapshot order = partiallyFilledOrder(request, "exec-1", fill);
        PaperAccountSnapshot account = applied(account(), request, fill);

        PaperReconciliationReport report =
                reconcile(session(account, order, order), List.of(order));

        assertConsistent(report, 1, 1, 1, "1", "buy-1", "100", fill.getFilledAt());
    }

    @Test
    void completeBuyThenPartialSellIsConsistentAndKeepsEntryPrice() {
        ExecutionOrderRequest buy = buy("buy-1", "4", T0);
        ExecutionFill entry = fill("entry", "4", "100", "0.4", T0.plusSeconds(3));
        ExecutionOrderSnapshot buyOrder = filledOrder(buy, "exec-buy", entry);
        PaperAccountSnapshot open = applied(account(), buy, entry);
        ExecutionOrderRequest sell = sell("sell-1", "4", T0.plusSeconds(4));
        ExecutionFill exit = fill("exit", "1", "110", "0.1", T0.plusSeconds(7));
        ExecutionOrderSnapshot sellOrder = partiallyFilledOrder(sell, "exec-sell", exit);
        PaperAccountSnapshot partial = applied(open, sell, exit);

        PaperReconciliationReport report = reconcile(
                session(partial, sellOrder, sellOrder), List.of(buyOrder, sellOrder));

        assertConsistent(report, 2, 2, 2, "3", "buy-1", "100", exit.getFilledAt());
    }

    @Test
    void completeRoundTripIsConsistentAndFlat() {
        ExecutionOrderRequest buy = buy("buy-1", "2", T0);
        ExecutionFill entry = fill("entry", "2", "100", "0.2", T0.plusSeconds(3));
        ExecutionOrderSnapshot buyOrder = filledOrder(buy, "exec-buy", entry);
        PaperAccountSnapshot open = applied(account(), buy, entry);
        ExecutionOrderRequest sell = sell("sell-1", "2", T0.plusSeconds(4));
        ExecutionFill exit = fill("exit", "2", "110", "0.2", T0.plusSeconds(7));
        ExecutionOrderSnapshot sellOrder = filledOrder(sell, "exec-sell", exit);
        PaperAccountSnapshot closed = applied(open, sell, exit);

        PaperReconciliationReport report = reconcile(
                session(closed, null, sellOrder), List.of(buyOrder, sellOrder));

        assertConsistent(report, 2, 2, 2, "0", null, "0", exit.getFilledAt());
    }

    @Test
    void duplicateClientOrderIdIsReported() {
        ExecutionOrderSnapshot first = orderMachine.create(buy("same", "1", T0));
        ExecutionOrderSnapshot second = orderMachine.create(buy("same", "1", T0.plusSeconds(1)));
        PaperAccountSnapshot updatedAccount = accountEngine.markToMarket(
                account(), SYMBOL, new BigDecimal("100"), T0.plusSeconds(1));

        assertCodes(
                reconcile(session(updatedAccount, null, second), List.of(first, second)),
                PaperReconciliationViolationCode.CLIENT_ORDER_ID_DUPLICATE);
    }

    @Test
    void duplicateExecutionOrderIdIsReported() {
        ExecutionOrderSnapshot first = submittedOrder(buy("buy-1", "1", T0), "same-exec");
        ExecutionOrderSnapshot second =
                submittedOrder(buy("buy-2", "1", T0.plusSeconds(4)), "same-exec");

        assertThat(codes(reconcile(
                session(account(), second, second), List.of(first, second))))
                .contains(PaperReconciliationViolationCode.EXECUTION_ORDER_ID_DUPLICATE);
    }

    @Test
    void duplicateFillApplicationKeyIsReported() {
        ExecutionOrderRequest request = buy("buy-1", "1", T0);
        ExecutionFill fill = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionOrderSnapshot first = filledOrder(request, "exec-1", fill);
        ExecutionOrderSnapshot second = ExecutionOrderSnapshotFixture.copy(
                first, first.getStatus(), "exec-2", first.getFilledQuantity(),
                first.getRemainingQuantity(), first.getAveragePrice(), first.getCumulativeFee(),
                first.getFeeAsset(), first.getFills(), T0.plusSeconds(4), first.getCompletedAt());

        assertThat(codes(reconcile(
                session(applied(account(), request, fill), null, second), List.of(first, second))))
                .contains(PaperReconciliationViolationCode.FILL_APPLICATION_KEY_DUPLICATE);
    }

    @Test
    void filledQuantityMismatchIsReported() {
        Baseline baseline = fullBuy();
        ExecutionOrderSnapshot corrupt = ExecutionOrderSnapshotFixture.copy(
                baseline.lastOrder, ExecutionOrderStatus.FILLED, "exec-1",
                new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("100"),
                new BigDecimal("0.2"), FEE_ASSET, baseline.lastOrder.getFills(),
                baseline.lastOrder.getLastUpdatedAt(), baseline.lastOrder.getCompletedAt());

        assertThat(codes(reconcile(
                session(baseline.account, null, corrupt), List.of(corrupt))))
                .contains(PaperReconciliationViolationCode.ORDER_FILLED_QUANTITY_MISMATCH);
    }

    @Test
    void remainingQuantityMismatchIsReported() {
        Baseline baseline = fullBuy();
        ExecutionOrderSnapshot corrupt = ExecutionOrderSnapshotFixture.copy(
                baseline.lastOrder, ExecutionOrderStatus.FILLED, "exec-1",
                new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("100"),
                new BigDecimal("0.2"), FEE_ASSET, baseline.lastOrder.getFills(),
                baseline.lastOrder.getLastUpdatedAt(), baseline.lastOrder.getCompletedAt());

        assertThat(codes(reconcile(
                session(baseline.account, null, corrupt), List.of(corrupt))))
                .contains(PaperReconciliationViolationCode.ORDER_REMAINING_QUANTITY_MISMATCH);
    }

    @Test
    void averagePriceMismatchIsReported() {
        Baseline baseline = fullBuy();
        ExecutionOrderSnapshot corrupt = ExecutionOrderSnapshotFixture.copy(
                baseline.lastOrder, ExecutionOrderStatus.FILLED, "exec-1",
                new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("101"),
                new BigDecimal("0.2"), FEE_ASSET, baseline.lastOrder.getFills(),
                baseline.lastOrder.getLastUpdatedAt(), baseline.lastOrder.getCompletedAt());

        assertThat(codes(reconcile(
                session(baseline.account, null, corrupt), List.of(corrupt))))
                .contains(PaperReconciliationViolationCode.ORDER_AVERAGE_PRICE_MISMATCH);
    }

    @Test
    void cumulativeFeeMismatchIsReported() {
        Baseline baseline = fullBuy();
        ExecutionOrderSnapshot corrupt = ExecutionOrderSnapshotFixture.copy(
                baseline.lastOrder, ExecutionOrderStatus.FILLED, "exec-1",
                new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("100"),
                new BigDecimal("0.3"), FEE_ASSET, baseline.lastOrder.getFills(),
                baseline.lastOrder.getLastUpdatedAt(), baseline.lastOrder.getCompletedAt());

        assertThat(codes(reconcile(
                session(baseline.account, null, corrupt), List.of(corrupt))))
                .contains(PaperReconciliationViolationCode.ORDER_CUMULATIVE_FEE_MISMATCH);
    }

    @Test
    void differentBigDecimalScalesDoNotViolateNumericEquality() {
        ExecutionOrderRequest request = buy("buy-1", "2.00", T0);
        ExecutionFill fill = fill("fill-1", "2.0", "100.00", "0.200", T0.plusSeconds(3));
        ExecutionOrderSnapshot generated = filledOrder(request, "exec-1", fill);
        ExecutionOrderSnapshot scaled = ExecutionOrderSnapshotFixture.copy(
                generated, ExecutionOrderStatus.FILLED, "exec-1",
                new BigDecimal("2.0000"), new BigDecimal("0.000"),
                new BigDecimal("100.0000"), new BigDecimal("0.20"), FEE_ASSET,
                generated.getFills(), generated.getLastUpdatedAt(), generated.getCompletedAt());
        PaperAccountSnapshot account = applied(account(), request, fill);

        assertThat(reconcile(session(account, null, scaled), List.of(scaled)).getStatus())
                .isEqualTo(PaperReconciliationStatus.CONSISTENT);
    }

    @Test
    void missingAccountFillIsReported() {
        Baseline baseline = fullBuy();

        assertThat(codes(reconcile(
                session(account(), null, baseline.lastOrder), List.of(baseline.lastOrder))))
                .contains(PaperReconciliationViolationCode.ACCOUNT_FILL_MISSING);
    }

    @Test
    void unexpectedAccountFillIsReported() {
        Baseline baseline = fullBuy();

        assertThat(codes(reconcile(session(baseline.account, null, null), List.of())))
                .contains(PaperReconciliationViolationCode.ACCOUNT_FILL_UNEXPECTED);
    }

    @Test
    void accountFillWithSameKeyAndDifferentContentIsReported() {
        ExecutionOrderRequest request = buy("buy-1", "2", T0);
        ExecutionFill orderFill = fill("fill-1", "2", "100", "0.2", T0.plusSeconds(3));
        ExecutionFill accountFill = fill("fill-1", "2", "101", "0.2", T0.plusSeconds(3));
        ExecutionOrderSnapshot order = filledOrder(request, "exec-1", orderFill);
        PaperAccountSnapshot account = applied(account(), request, accountFill);

        assertThat(codes(reconcile(session(account, null, order), List.of(order))))
                .contains(PaperReconciliationViolationCode.ACCOUNT_FILL_CONTENT_MISMATCH);
    }

    @Test
    void pendingOrderMissingFromHistoryIsReported() {
        ExecutionOrderSnapshot pending = submittedOrder(buy("buy-1", "1", T0), "exec-1");

        assertThat(codes(reconcile(session(account(), pending, pending), List.of())))
                .contains(PaperReconciliationViolationCode.PENDING_ORDER_MISSING_FROM_HISTORY);
    }

    @Test
    void lastOrderMustBeFinalHistoryItem() {
        ExecutionOrderSnapshot first = orderMachine.create(buy("buy-1", "1", T0));
        ExecutionOrderSnapshot second =
                orderMachine.create(buy("buy-2", "1", T0.plusSeconds(1)));

        assertThat(codes(reconcile(
                session(account(), null, first), List.of(first, second))))
                .contains(PaperReconciliationViolationCode.LAST_ORDER_MISMATCH);
    }

    @Test
    void multipleActiveOrdersAreReported() {
        ExecutionOrderSnapshot first = submittedOrder(buy("buy-1", "1", T0), "exec-1");
        ExecutionOrderSnapshot second =
                submittedOrder(buy("buy-2", "1", T0.plusSeconds(4)), "exec-2");

        assertThat(codes(reconcile(
                session(account(), second, second), List.of(first, second))))
                .contains(PaperReconciliationViolationCode.MULTIPLE_ACTIVE_ORDERS);
    }

    @Test
    void newBuyWhilePositionOpenReportsPyramiding() {
        ExecutionOrderRequest firstRequest = buy("buy-1", "1", T0);
        ExecutionFill firstFill = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionOrderSnapshot first = filledOrder(firstRequest, "exec-1", firstFill);
        ExecutionOrderRequest secondRequest = buy("buy-2", "1", T0.plusSeconds(4));
        ExecutionFill secondFill =
                fill("fill-2", "1", "101", "0.1", T0.plusSeconds(7));
        ExecutionOrderSnapshot second = filledOrder(secondRequest, "exec-2", secondFill);

        assertThat(codes(reconcile(
                session(applied(account(), firstRequest, firstFill), null, second),
                List.of(first, second))))
                .contains(PaperReconciliationViolationCode.POSITION_PYRAMIDING_DETECTED);
    }

    @Test
    void sellBeyondPositionReportsNegativeReplayQuantity() {
        ExecutionOrderRequest sell = sell("sell-1", "2", T0);
        ExecutionFill fill = fill("fill-1", "2", "100", "0.2", T0.plusSeconds(3));
        ExecutionOrderSnapshot order = filledOrder(sell, "exec-1", fill);

        assertThat(codes(reconcile(session(account(), null, order), List.of(order))))
                .contains(PaperReconciliationViolationCode.POSITION_QUANTITY_NEGATIVE);
    }

    @Test
    void replayQuantityMismatchWithAccountIsReported() {
        Baseline baseline = fullBuy();
        PaperAccountSnapshot wrong = PaperAccountSnapshotFixture.copy(
                baseline.account,
                PaperAccountSnapshotFixture.openPosition(
                        SYMBOL, BigDecimal.ONE, new BigDecimal("100"), "buy-1"),
                baseline.account.getAppliedFills(),
                baseline.account.getLastUpdatedAt());

        assertThat(codes(reconcile(
                session(wrong, null, baseline.lastOrder), List.of(baseline.lastOrder))))
                .contains(PaperReconciliationViolationCode.POSITION_QUANTITY_MISMATCH);
    }

    @Test
    void openingClientOrderIdMismatchIsReported() {
        Baseline baseline = fullBuy();
        PaperAccountSnapshot wrong = PaperAccountSnapshotFixture.copy(
                baseline.account,
                PaperAccountSnapshotFixture.openPosition(
                        SYMBOL, new BigDecimal("2"), new BigDecimal("100"), "other-buy"),
                baseline.account.getAppliedFills(),
                baseline.account.getLastUpdatedAt());

        assertThat(codes(reconcile(
                session(wrong, null, baseline.lastOrder), List.of(baseline.lastOrder))))
                .contains(PaperReconciliationViolationCode.POSITION_OPENING_ORDER_MISMATCH);
    }

    @Test
    void averageEntryPriceMismatchIsReported() {
        Baseline baseline = fullBuy();
        PaperAccountSnapshot wrong = PaperAccountSnapshotFixture.copy(
                baseline.account,
                PaperAccountSnapshotFixture.openPosition(
                        SYMBOL, new BigDecimal("2"), new BigDecimal("101"), "buy-1"),
                baseline.account.getAppliedFills(),
                baseline.account.getLastUpdatedAt());

        assertThat(codes(reconcile(
                session(wrong, null, baseline.lastOrder), List.of(baseline.lastOrder))))
                .contains(PaperReconciliationViolationCode.POSITION_AVERAGE_ENTRY_PRICE_MISMATCH);
    }

    @Test
    void accountLastUpdatedBeforeLatestFillIsReported() {
        Baseline baseline = fullBuy();
        PaperAccountSnapshot wrong = PaperAccountSnapshotFixture.copy(
                baseline.account,
                baseline.account.getPosition(),
                baseline.account.getAppliedFills(),
                T0.plusSeconds(2));

        assertThat(codes(reconcile(
                session(wrong, null, baseline.lastOrder), List.of(baseline.lastOrder))))
                .contains(PaperReconciliationViolationCode.ACCOUNT_TIME_INVALID);
    }

    @Test
    void returnsAllViolationsInStableCheckOrder() {
        ExecutionOrderSnapshot first = orderMachine.create(buy("same", "1", T0.plusSeconds(2)));
        ExecutionOrderSnapshot second = orderMachine.create(buy("same", "1", T0));
        PaperTradingSessionSnapshot session = session(account(), null, second);
        PaperReconciliationRequest request =
                new PaperReconciliationRequest(session, List.of(first, second), RECONCILED_AT);

        PaperReconciliationReport firstReport = engine.reconcile(request);
        PaperReconciliationReport secondReport = engine.reconcile(request);

        assertThat(codes(firstReport)).containsExactly(
                PaperReconciliationViolationCode.CLIENT_ORDER_ID_DUPLICATE,
                PaperReconciliationViolationCode.ORDER_HISTORY_UNSORTED);
        assertThat(secondReport).isEqualTo(firstReport);
        assertThat(firstReport.getReconciledAt()).isEqualTo(RECONCILED_AT);
    }

    @Test
    void requestCopiesCallerOrderListAndRejectsInvalidInputs() {
        List<ExecutionOrderSnapshot> mutable = new ArrayList<>();
        PaperReconciliationRequest request =
                new PaperReconciliationRequest(session(account(), null, null), mutable, RECONCILED_AT);
        mutable.add(orderMachine.create(buy("later", "1", T0)));

        assertThat(engine.reconcile(request).getOrderCount()).isZero();
        assertThatThrownBy(() -> engine.reconcile(null))
                .isInstanceOf(PaperReconciliationException.class);
        assertThatThrownBy(() -> engine.reconcile(
                new PaperReconciliationRequest(null, List.of(), RECONCILED_AT)))
                .isInstanceOf(PaperReconciliationException.class);
        assertThatThrownBy(() -> engine.reconcile(
                new PaperReconciliationRequest(session(account(), null, null), null, RECONCILED_AT)))
                .isInstanceOf(PaperReconciliationException.class);
        assertThatThrownBy(() -> engine.reconcile(
                new PaperReconciliationRequest(
                        session(account(), null, null),
                        java.util.Collections.singletonList(null),
                        RECONCILED_AT)))
                .isInstanceOf(PaperReconciliationException.class);
    }

    private Baseline fullBuy() {
        ExecutionOrderRequest request = buy("buy-1", "2", T0);
        ExecutionFill fill = fill("fill-1", "2", "100", "0.2", T0.plusSeconds(3));
        return new Baseline(
                filledOrder(request, "exec-1", fill),
                applied(account(), request, fill));
    }

    private ExecutionOrderSnapshot submittedOrder(
            ExecutionOrderRequest request, String executionOrderId) {
        ExecutionOrderSnapshot created = orderMachine.create(request);
        ExecutionOrderSnapshot accepted =
                orderMachine.accept(created, request.getRequestedAt().plusSeconds(1));
        return orderMachine.submit(
                accepted, executionOrderId, request.getRequestedAt().plusSeconds(2));
    }

    private ExecutionOrderSnapshot partiallyFilledOrder(
            ExecutionOrderRequest request,
            String executionOrderId,
            ExecutionFill fill) {
        return orderMachine.applyFill(submittedOrder(request, executionOrderId), fill);
    }

    private ExecutionOrderSnapshot filledOrder(
            ExecutionOrderRequest request,
            String executionOrderId,
            ExecutionFill fill) {
        return orderMachine.applyFill(submittedOrder(request, executionOrderId), fill);
    }

    private PaperAccountSnapshot account() {
        return accountEngine.initialize(
                "paper-1", PROVIDER, MARKET_TYPE, FEE_ASSET,
                new BigDecimal("10000"), LocalDate.of(2026, 7, 28), T0);
    }

    private PaperAccountSnapshot applied(
            PaperAccountSnapshot account,
            ExecutionOrderRequest request,
            ExecutionFill fill) {
        return accountEngine.applyFill(account, request, fill).getAccount();
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
                clientOrderId, PROVIDER, MARKET_TYPE, SYMBOL, ExecutionOrderType.MARKET,
                side, PositionSide.LONG, new BigDecimal(quantity), reduceOnly, requestedAt);
    }

    private ExecutionFill fill(
            String fillId,
            String quantity,
            String price,
            String fee,
            Instant filledAt) {
        return new ExecutionFill(
                fillId, new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(fee), FEE_ASSET, filledAt);
    }

    private PaperTradingSessionSnapshot session(
            PaperAccountSnapshot account,
            ExecutionOrderSnapshot pending,
            ExecutionOrderSnapshot last) {
        return new PaperTradingSessionSnapshot(
                config(), account, pending, last, null, null, null, null,
                account.getLastUpdatedAt());
    }

    private PaperTradingSessionConfig config() {
        return new PaperTradingSessionConfig(
                "session-1", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "TEST", "1", Map.of("period", 3),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                new BigDecimal("2"), null,
                new MarketOrderQuantityRules(
                        PROVIDER, MARKET_TYPE, SYMBOL, FEE_ASSET, 3,
                        new BigDecimal("0.001"), new BigDecimal("0.001"),
                        new BigDecimal("1000"), new BigDecimal("5")),
                BigDecimal.ONE,
                new PreTradeRiskPolicy(
                        new BigDecimal("0.90"), new BigDecimal("0.90"),
                        new BigDecimal("0.01"), new BigDecimal("0.50"), 5),
                new SimulatedExecutionPolicy(
                        new BigDecimal("0.001"), FEE_ASSET, BigDecimal.ZERO));
    }

    private PaperReconciliationReport reconcile(
            PaperTradingSessionSnapshot session,
            List<ExecutionOrderSnapshot> history) {
        return engine.reconcile(
                new PaperReconciliationRequest(session, history, RECONCILED_AT));
    }

    private void assertConsistent(
            PaperReconciliationReport report,
            int orderCount,
            int orderFillCount,
            int accountFillCount,
            String quantity,
            String openingOrderId,
            String averageEntryPrice,
            Instant latestFillTime) {
        assertThat(report.getStatus()).isEqualTo(PaperReconciliationStatus.CONSISTENT);
        assertThat(report.getViolations()).isEmpty();
        assertThat(report.getOrderCount()).isEqualTo(orderCount);
        assertThat(report.getOrderFillCount()).isEqualTo(orderFillCount);
        assertThat(report.getAccountAppliedFillCount()).isEqualTo(accountFillCount);
        assertThat(report.getDerivedPositionQuantity()).isEqualByComparingTo(quantity);
        assertThat(report.getDerivedOpeningClientOrderId()).isEqualTo(openingOrderId);
        assertThat(report.getDerivedAverageEntryPrice()).isEqualByComparingTo(averageEntryPrice);
        assertThat(report.getLatestFillTime()).isEqualTo(latestFillTime);
        assertThat(report.getReconciledAt()).isEqualTo(RECONCILED_AT);
    }

    private void assertCodes(
            PaperReconciliationReport report,
            PaperReconciliationViolationCode... expected) {
        assertThat(codes(report)).containsExactly(expected);
    }

    private List<PaperReconciliationViolationCode> codes(PaperReconciliationReport report) {
        return report.getViolations().stream()
                .map(PaperReconciliationViolation::getCode)
                .toList();
    }

    private static final class Baseline {
        private final ExecutionOrderSnapshot lastOrder;
        private final PaperAccountSnapshot account;

        private Baseline(
                ExecutionOrderSnapshot lastOrder,
                PaperAccountSnapshot account) {
            this.lastOrder = lastOrder;
            this.account = account;
        }
    }
}
