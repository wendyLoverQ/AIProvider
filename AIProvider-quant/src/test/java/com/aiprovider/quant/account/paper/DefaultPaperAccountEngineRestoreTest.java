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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperAccountEngineRestoreTest {
    private static final Instant T0 = Instant.parse("2026-07-28T00:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 28);
    private static final String SYMBOL = "BTCUSDT";
    private final DefaultPaperAccountEngine engine = new DefaultPaperAccountEngine();

    @Test
    void restoresZeroFillAndOpenAccountsExactly() {
        PaperAccountSnapshot flat = engine.initialize("paper-1", MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL, "USDT", new BigDecimal("10000"), DAY, T0);
        PaperAccountSnapshot restoredFlat = engine.restore(request(flat));
        assertThat(restoredFlat).isEqualTo(flat);
        assertThat(restoredFlat.getAppliedFills()).isEmpty();

        PaperAccountSnapshot open = engine.applyFill(flat, buy("open-1", "2", T0),
                fill("fill-1", "2", "100", "1", T0.plusSeconds(1))).getAccount();
        PaperAccountSnapshot restoredOpen = engine.restore(request(open));
        assertThat(restoredOpen).isEqualTo(open);
    }

    @Test
    void zeroFillAccountCanContinueValuationAndAcceptFirstRealFill() {
        PaperAccountSnapshot restored = engine.restore(request(account()));
        PaperAccountSnapshot marked = engine.markToMarket(restored, SYMBOL, new BigDecimal("120"),
                T0.plusSeconds(1));
        ExecutionFill firstRealFill = fill("fill-1", "1", "121", "0.1", T0.plusSeconds(2));
        PaperAccountSnapshot updated = engine.applyFill(marked, buy("open-1", "1", T0.plusSeconds(1)),
                firstRealFill).getAccount();

        assertThat(updated.getAppliedFills()).hasSize(1);
        assertThat(updated.getAppliedFills().get(0).getClientOrderId()).isEqualTo("open-1");
        assertThat(updated.getAppliedFills().get(0).getFillId()).isEqualTo(firstRealFill.getFillId());
    }

    @Test
    void rejectsNullAppliedFillListButAcceptsEmptyList() {
        PaperAccountSnapshot original = account();
        PaperAccountRestoreRequest nullList = request(original, (List<PaperAppliedFill>) null);
        assertThatThrownBy(() -> engine.restore(nullList))
                .isInstanceOf(PaperAccountException.class)
                .hasMessageContaining("appliedFills is required")
                .extracting(e -> ((PaperAccountException) e).getErrorCode())
                .isEqualTo(PaperAccountException.PAPER_ACCOUNT_RESTORE_INVALID);

        PaperAccountSnapshot restored = engine.restore(request(original));
        assertThat(restored.getAppliedFills()).isEmpty();
    }

    @Test
    void rejectsPositionAndAccountArithmeticInconsistency() {
        PaperAccountSnapshot open = engine.applyFill(account(), buy("open-1", "2", T0),
                fill("fill-1", "2", "100", "1", T0.plusSeconds(1))).getAccount();
        PaperAccountRestoreRequest badNotional = request(open, new BigDecimal("201"),
                open.getPosition().getUnrealizedPnl(), open.getTotalEquity(), open.getAvailableCapital(),
                open.getPosition().getQuantity());
        assertRestoreInvalid(badNotional);

        PaperAccountRestoreRequest badEquity = request(open, open.getPosition().getPositionNotional(),
                open.getPosition().getUnrealizedPnl(), new BigDecimal("10001"), open.getAvailableCapital(),
                open.getPosition().getQuantity());
        assertRestoreInvalid(badEquity);

        PaperAccountRestoreRequest flatNonZero = request(account(), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10000"), new BigDecimal("10000"), BigDecimal.ONE);
        assertRestoreInvalid(flatNonZero);
    }

    @Test
    void rejectsDuplicateFillFeeAssetAndTimeViolations() {
        PaperAppliedFill first = PaperAppliedFill.from("order-1", fill("fill-1", "1", "100", "1", T0));
        PaperAccountSnapshot flat = account();
        PaperAccountRestoreRequest duplicate = request(flat, List.of(first, first));
        assertRestoreInvalid(duplicate);

        PaperAppliedFill wrongAsset = PaperAppliedFill.from("order-1", new ExecutionFill(
                "fill-1", BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ONE, "BNB", T0));
        assertRestoreInvalid(request(flat, List.of(wrongAsset)));

        PaperAppliedFill afterLastUpdate = PaperAppliedFill.from("order-1", fill(
                "fill-1", "1", "100", "1", T0.plusSeconds(1)));
        assertRestoreInvalid(request(flat, List.of(afterLastUpdate)));

        List<PaperAppliedFill> nullElement = new ArrayList<>();
        nullElement.add(null);
        assertRestoreInvalid(request(flat, nullElement));
    }

    @Test
    void restoreRequestCopiesAppliedFillList() {
        PaperAppliedFill fill = PaperAppliedFill.from("order-1", fill("fill-1", "1", "100", "0", T0));
        List<PaperAppliedFill> source = new ArrayList<>(List.of(fill));
        PaperAccountRestoreRequest request = request(account(), source);
        source.clear();
        assertThat(request.getAppliedFills()).hasSize(1);
        assertThatThrownBy(() -> request.getAppliedFills().clear()).isInstanceOf(UnsupportedOperationException.class);

        PaperAccountRestoreRequest emptyRequest = request(account());
        assertThat(emptyRequest.getAppliedFills()).isEmpty();
        assertThatThrownBy(() -> emptyRequest.getAppliedFills().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private PaperAccountSnapshot account() {
        return engine.initialize("paper-1", MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL,
                "USDT", new BigDecimal("10000"), DAY, T0);
    }

    private PaperAccountRestoreRequest request(PaperAccountSnapshot snapshot) {
        PaperPositionSnapshot position = snapshot.getPosition();
        return request(snapshot, position.getPositionNotional(), position.getUnrealizedPnl(),
                snapshot.getTotalEquity(), snapshot.getAvailableCapital(), position.getQuantity(),
                snapshot.getAppliedFills());
    }

    private PaperAccountRestoreRequest request(
            PaperAccountSnapshot snapshot,
            BigDecimal positionNotional,
            BigDecimal positionUnrealizedPnl,
            BigDecimal totalEquity,
            BigDecimal availableCapital,
            BigDecimal positionQuantity) {
        return request(snapshot, positionNotional, positionUnrealizedPnl, totalEquity,
                availableCapital, positionQuantity, snapshot.getAppliedFills());
    }

    private PaperAccountRestoreRequest request(
            PaperAccountSnapshot snapshot, List<PaperAppliedFill> fills) {
        PaperPositionSnapshot position = snapshot.getPosition();
        return request(snapshot, position.getPositionNotional(), position.getUnrealizedPnl(),
                snapshot.getTotalEquity(), snapshot.getAvailableCapital(), position.getQuantity(), fills);
    }

    private PaperAccountRestoreRequest request(
            PaperAccountSnapshot snapshot,
            BigDecimal positionNotional,
            BigDecimal positionUnrealizedPnl,
            BigDecimal totalEquity,
            BigDecimal availableCapital,
            BigDecimal positionQuantity,
            List<PaperAppliedFill> fills) {
        PaperPositionSnapshot position = snapshot.getPosition();
        return new PaperAccountRestoreRequest(snapshot.getAccountId(), snapshot.getProvider(),
                snapshot.getMarketType(), snapshot.getQuoteAsset(), snapshot.getInitialCapital(),
                snapshot.getRealizedPnl(), snapshot.getUnrealizedPnl(), totalEquity, availableCapital,
                position.isOpen(), position.getSymbol(), positionQuantity, position.getAverageEntryPrice(),
                position.getMarkPrice(), positionNotional, positionUnrealizedPnl,
                position.getOpeningClientOrderId(), position.getOpenTradeNetPnl(),
                snapshot.getTradingDayState().getUtcDate(), snapshot.getTradingDayState().getDayStartEquity(),
                snapshot.getTradingDayState().getDailyRealizedPnl(), snapshot.getConsecutiveLosses(), fills,
                snapshot.getLastUpdatedAt());
    }

    private void assertRestoreInvalid(PaperAccountRestoreRequest request) {
        assertThatThrownBy(() -> engine.restore(request)).isInstanceOf(PaperAccountException.class)
                .extracting(e -> ((PaperAccountException) e).getErrorCode())
                .isEqualTo(PaperAccountException.PAPER_ACCOUNT_RESTORE_INVALID);
    }

    private ExecutionOrderRequest buy(String clientOrderId, String quantity, Instant requestedAt) {
        return new ExecutionOrderRequest(clientOrderId, MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL, SYMBOL, ExecutionOrderType.MARKET, OrderSide.BUY,
                PositionSide.LONG, new BigDecimal(quantity), false, requestedAt);
    }

    private ExecutionFill fill(String id, String quantity, String price, String fee, Instant filledAt) {
        return new ExecutionFill(id, new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(fee), "USDT", filledAt);
    }
}
