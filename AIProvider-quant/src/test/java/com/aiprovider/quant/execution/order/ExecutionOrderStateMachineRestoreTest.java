package com.aiprovider.quant.execution.order;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionOrderStateMachineRestoreTest {
    private static final Instant T0 = Instant.parse("2026-07-28T00:00:00Z");
    private final ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();

    @Test
    void restoresEverySupportedStateThroughTransitions() {
        ExecutionOrderSnapshot created = machine.create(order("2"));
        ExecutionOrderSnapshot accepted = machine.accept(created, T0.plusSeconds(1));
        ExecutionOrderSnapshot submitted = machine.submit(accepted, "exchange-1", T0.plusSeconds(2));
        ExecutionFill first = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionFill second = fill("fill-2", "1", "120", "0.2", T0.plusSeconds(4));
        ExecutionOrderSnapshot partial = machine.applyFill(submitted, first);
        ExecutionOrderSnapshot filled = machine.applyFill(partial, second);
        ExecutionOrderSnapshot canceled = machine.cancel(partial, T0.plusSeconds(5));
        ExecutionOrderSnapshot rejectedFromCreated = machine.reject(created, "R", "rejected", T0.plusSeconds(1));
        ExecutionOrderSnapshot rejectedFromAccepted = machine.reject(accepted, "R", "rejected", T0.plusSeconds(2));
        ExecutionOrderSnapshot failedAccepted = machine.fail(accepted, "F", "failed", T0.plusSeconds(2));
        ExecutionOrderSnapshot failedSubmitted = machine.fail(submitted, "F", "failed", T0.plusSeconds(3));
        ExecutionOrderSnapshot failedPartial = machine.fail(partial, "F", "failed", T0.plusSeconds(4));

        for (ExecutionOrderSnapshot snapshot : List.of(created, accepted, submitted, partial, filled,
                canceled, rejectedFromCreated, rejectedFromAccepted, failedAccepted, failedSubmitted,
                failedPartial)) {
            assertThat(machine.restore(request(snapshot))).isEqualTo(snapshot);
        }
    }

    @Test
    void restoredSubmittedOrderCanContinueApplyingFills() {
        ExecutionOrderSnapshot submitted = machine.submit(
                machine.accept(machine.create(order("2")), T0.plusSeconds(1)),
                "exchange-1", T0.plusSeconds(2));
        ExecutionOrderSnapshot restored = machine.restore(request(submitted));
        ExecutionOrderSnapshot filled = machine.applyFill(restored,
                fill("fill-1", "2", "101", "0.2", T0.plusSeconds(3)));

        assertThat(filled.getStatus()).isEqualTo(ExecutionOrderStatus.FILLED);
        assertThat(filled.getFilledQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void comparesDecimalsByValueAndRejectsFieldMismatch() {
        ExecutionOrderSnapshot partial = machine.applyFill(
                machine.submit(machine.accept(machine.create(order("2")), T0.plusSeconds(1)),
                        "exchange-1", T0.plusSeconds(2)),
                fill("fill-1", "1", "100", "0.10", T0.plusSeconds(3)));
        ExecutionOrderRestoreRequest scaled = new ExecutionOrderRestoreRequest(
                partial.getRequest(), partial.getStatus(), partial.getExecutionOrderId(),
                new BigDecimal("1.0"), new BigDecimal("1.0"), new BigDecimal("100.00"),
                new BigDecimal("0.100"), partial.getFeeAsset(), partial.getFills(),
                partial.getAcceptedAt(), partial.getSubmittedAt(), partial.getLastUpdatedAt(),
                partial.getCompletedAt(), partial.getTerminalErrorCode(), partial.getTerminalErrorMessage());
        assertThat(machine.restore(scaled)).isEqualTo(partial);

        ExecutionOrderRestoreRequest mismatch = new ExecutionOrderRestoreRequest(
                partial.getRequest(), partial.getStatus(), partial.getExecutionOrderId(),
                new BigDecimal("1.1"), partial.getRemainingQuantity(), partial.getAveragePrice(),
                partial.getCumulativeFee(), partial.getFeeAsset(), partial.getFills(),
                partial.getAcceptedAt(), partial.getSubmittedAt(), partial.getLastUpdatedAt(),
                partial.getCompletedAt(), partial.getTerminalErrorCode(), partial.getTerminalErrorMessage());
        assertRestoreError(mismatch, ExecutionOrderStateMachine.EXECUTION_ORDER_RESTORE_MISMATCH);
    }

    @Test
    void rejectsDuplicateIdsTimeRegressionAndMissingTerminalFields() {
        ExecutionOrderSnapshot submitted = machine.submit(
                machine.accept(machine.create(order("2")), T0.plusSeconds(1)),
                "exchange-1", T0.plusSeconds(2));
        ExecutionFill first = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionOrderRestoreRequest duplicate = requestWith(submitted, ExecutionOrderStatus.PARTIALLY_FILLED,
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("0.1"),
                "USDT", List.of(first, first), T0.plusSeconds(1), T0.plusSeconds(2), T0.plusSeconds(3),
                null, null, null);
        assertRestoreError(duplicate, ExecutionOrderStateMachine.EXECUTION_ORDER_RESTORE_INVALID);

        ExecutionFill late = fill("fill-2", "1", "100", "0.1", T0.plusSeconds(2));
        ExecutionOrderRestoreRequest timeRegression = requestWith(submitted, ExecutionOrderStatus.FILLED,
                new BigDecimal("2"), BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("0.2"),
                "USDT", List.of(first, late), T0.plusSeconds(1), T0.plusSeconds(2), T0.plusSeconds(2),
                T0.plusSeconds(2), null, null);
        assertRestoreError(timeRegression, ExecutionOrderStateMachine.EXECUTION_ORDER_RESTORE_INVALID);

        ExecutionOrderRestoreRequest missingError = requestWith(submitted, ExecutionOrderStatus.FAILED,
                BigDecimal.ZERO, new BigDecimal("2"), null, BigDecimal.ZERO, null, List.of(),
                T0.plusSeconds(1), null, T0.plusSeconds(1), T0.plusSeconds(2), null, null);
        assertRestoreError(missingError, ExecutionOrderStateMachine.EXECUTION_ORDER_RESTORE_INVALID);
    }

    @Test
    void restoreRequestCopiesFillList() {
        ExecutionOrderSnapshot submitted = machine.submit(
                machine.accept(machine.create(order("2")), T0.plusSeconds(1)),
                "exchange-1", T0.plusSeconds(2));
        List<ExecutionFill> source = new ArrayList<>();
        ExecutionOrderRestoreRequest request = requestWith(submitted, ExecutionOrderStatus.SUBMITTED,
                submitted.getFilledQuantity(), submitted.getRemainingQuantity(), null,
                submitted.getCumulativeFee(), null, source, submitted.getAcceptedAt(),
                submitted.getSubmittedAt(), submitted.getLastUpdatedAt(), null, null, null);
        source.add(fill("not-in-request", "1", "100", "0", T0.plusSeconds(3)));
        assertThat(request.getFills()).isEmpty();
        assertThatThrownBy(() -> request.getFills().add(source.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ExecutionOrderRestoreRequest request(ExecutionOrderSnapshot snapshot) {
        return requestWith(snapshot, snapshot.getStatus(), snapshot.getFilledQuantity(),
                snapshot.getRemainingQuantity(), snapshot.getAveragePrice(), snapshot.getCumulativeFee(),
                snapshot.getFeeAsset(), snapshot.getFills(), snapshot.getAcceptedAt(), snapshot.getSubmittedAt(),
                snapshot.getLastUpdatedAt(), snapshot.getCompletedAt(), snapshot.getTerminalErrorCode(),
                snapshot.getTerminalErrorMessage());
    }

    private ExecutionOrderRestoreRequest requestWith(
            ExecutionOrderSnapshot base,
            ExecutionOrderStatus status,
            BigDecimal filledQuantity,
            BigDecimal remainingQuantity,
            BigDecimal averagePrice,
            BigDecimal cumulativeFee,
            String feeAsset,
            List<ExecutionFill> fills,
            Instant acceptedAt,
            Instant submittedAt,
            Instant lastUpdatedAt,
            Instant completedAt,
            String terminalErrorCode,
            String terminalErrorMessage) {
        return new ExecutionOrderRestoreRequest(base.getRequest(), status, base.getExecutionOrderId(),
                filledQuantity, remainingQuantity, averagePrice, cumulativeFee, feeAsset, fills,
                acceptedAt, submittedAt, lastUpdatedAt, completedAt, terminalErrorCode, terminalErrorMessage);
    }

    private void assertRestoreError(ExecutionOrderRestoreRequest request, String code) {
        assertThatThrownBy(() -> machine.restore(request)).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo(code);
    }

    private ExecutionOrderRequest order(String quantity) {
        return new ExecutionOrderRequest("client-1", MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL, "BTCUSDT", ExecutionOrderType.MARKET, OrderSide.BUY,
                PositionSide.LONG, new BigDecimal(quantity), false, T0);
    }

    private ExecutionFill fill(String id, String quantity, String price, String fee, Instant filledAt) {
        return new ExecutionFill(id, new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(fee), "USDT", filledAt);
    }
}
