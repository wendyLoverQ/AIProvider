package com.aiprovider.quant.execution.order;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionOrderStateMachineTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsAcceptsSubmitsAndFillsOrder() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot created = machine.create(request(new BigDecimal("2"), false, OrderSide.BUY));
        ExecutionOrderSnapshot accepted = machine.accept(created, T0.plusSeconds(1));
        ExecutionOrderSnapshot submitted = machine.submit(accepted, "exchange-1", T0.plusSeconds(2));
        ExecutionOrderSnapshot filled = machine.applyFill(submitted, fill("fill-1", "2", "101", "0.2", T0.plusSeconds(3)));

        assertThat(created.getStatus()).isEqualTo(ExecutionOrderStatus.CREATED);
        assertThat(filled.getStatus()).isEqualTo(ExecutionOrderStatus.FILLED);
        assertThat(filled.getFilledQuantity()).isEqualByComparingTo("2");
        assertThat(filled.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(filled.getAveragePrice()).isEqualByComparingTo("101");
        assertThat(filled.getCompletedAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void calculatesWeightedAverageForPartialFills() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot submitted = submitted(machine, new BigDecimal("4"));
        ExecutionOrderSnapshot partial = machine.applyFill(submitted, fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3)));
        ExecutionOrderSnapshot filled = machine.applyFill(partial, fill("fill-2", "3", "120", "0.3", T0.plusSeconds(4)));

        assertThat(partial.getStatus()).isEqualTo(ExecutionOrderStatus.PARTIALLY_FILLED);
        assertThat(partial.getAveragePrice()).isEqualByComparingTo("100");
        assertThat(filled.getAveragePrice()).isEqualByComparingTo("115");
        assertThat(filled.getCumulativeFee()).isEqualByComparingTo("0.4");
        assertThat(filled.getFeeAsset()).isEqualTo("USDT");
    }

    @Test
    void partialCancelPreservesFillsAndRemainingQuantity() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot partial = machine.applyFill(submitted(machine, new BigDecimal("4")),
                fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3)));
        ExecutionOrderSnapshot canceled = machine.cancel(partial, T0.plusSeconds(4));

        assertThat(canceled.getStatus()).isEqualTo(ExecutionOrderStatus.CANCELED);
        assertThat(canceled.getFills()).containsExactly(partial.getFills().get(0));
        assertThat(canceled.getFilledQuantity()).isEqualByComparingTo("1");
        assertThat(canceled.getRemainingQuantity()).isEqualByComparingTo("3");
        assertThat(canceled.getAveragePrice()).isEqualByComparingTo("100");
    }

    @Test
    void rejectsDuplicateAndOverfilledFills() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot submitted = submitted(machine, new BigDecimal("2"));
        ExecutionFill first = fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3));
        ExecutionOrderSnapshot partial = machine.applyFill(submitted, first);
        assertThatThrownBy(() -> machine.applyFill(partial, first)).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_DUPLICATE_FILL");
        assertThatThrownBy(() -> machine.applyFill(partial, fill("fill-2", "2", "100", "0.1", T0.plusSeconds(4))))
                .isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_OVERFILLED");
    }

    @Test
    void terminalOrdersCannotMigrateOrReceiveFills() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot filled = machine.applyFill(submitted(machine, new BigDecimal("1")),
                fill("fill-1", "1", "100", "0", T0.plusSeconds(3)));
        assertThatThrownBy(() -> machine.applyFill(filled, fill("fill-2", "1", "100", "0", T0.plusSeconds(4))))
                .isInstanceOf(ExecutionOrderException.class).extracting(e -> ((ExecutionOrderException) e).getErrorCode())
                .isEqualTo("EXECUTION_ORDER_TRANSITION_INVALID");
        assertThatThrownBy(() -> machine.cancel(filled, T0.plusSeconds(4))).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_TRANSITION_INVALID");
    }

    @Test
    void rejectsIllegalTransitionsAndTimeRegression() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot created = machine.create(request(new BigDecimal("1"), false, OrderSide.BUY));
        assertThatThrownBy(() -> machine.submit(created, "exchange-1", T0.plusSeconds(2))).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_TRANSITION_INVALID");
        assertThatThrownBy(() -> machine.accept(created, T0.minusSeconds(1))).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_TIME_INVALID");
    }

    @Test
    void rejectsInvalidSideReduceOnlyCombinations() {
        assertThatThrownBy(() -> request(new BigDecimal("1"), true, OrderSide.BUY)).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_REQUEST_INVALID");
        assertThatThrownBy(() -> request(new BigDecimal("1"), false, OrderSide.SELL)).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_REQUEST_INVALID");
    }

    @Test
    void rejectsFeeAssetChangeAndFillTimeRegression() {
        ExecutionOrderStateMachine machine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot partial = machine.applyFill(submitted(machine, new BigDecimal("2")),
                fill("fill-1", "1", "100", "0.1", T0.plusSeconds(3)));
        ExecutionFill changedAsset = new ExecutionFill("fill-2", new BigDecimal("1"), new BigDecimal("100"),
                new BigDecimal("0.1"), "BNB", T0.plusSeconds(4));
        assertThatThrownBy(() -> machine.applyFill(partial, changedAsset)).isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_FEE_ASSET_CONFLICT");
        assertThatThrownBy(() -> machine.applyFill(partial, fill("fill-2", "1", "100", "0.1", T0.plusSeconds(2))))
                .isInstanceOf(ExecutionOrderException.class)
                .extracting(e -> ((ExecutionOrderException) e).getErrorCode()).isEqualTo("EXECUTION_ORDER_TIME_INVALID");
    }

    @Test
    void identicalInputsProduceIdenticalSnapshots() {
        ExecutionOrderStateMachine firstMachine = new ExecutionOrderStateMachine();
        ExecutionOrderStateMachine secondMachine = new ExecutionOrderStateMachine();
        ExecutionOrderSnapshot first = firstMachine.applyFill(submitted(firstMachine, new BigDecimal("2")),
                fill("fill-1", "2", "100", "0.1", T0.plusSeconds(3)));
        ExecutionOrderSnapshot second = secondMachine.applyFill(submitted(secondMachine, new BigDecimal("2")),
                fill("fill-1", "2", "100", "0.1", T0.plusSeconds(3)));
        assertThat(first).isEqualTo(second);
    }

    private ExecutionOrderSnapshot submitted(ExecutionOrderStateMachine machine, BigDecimal quantity) {
        return machine.submit(machine.accept(machine.create(request(quantity, false, OrderSide.BUY)), T0.plusSeconds(1)),
                "exchange-1", T0.plusSeconds(2));
    }

    private ExecutionOrderRequest request(BigDecimal quantity, boolean reduceOnly, OrderSide side) {
        return new ExecutionOrderRequest("client-1", MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL,
                "BTCUSDT", ExecutionOrderType.MARKET, side, PositionSide.LONG, quantity, reduceOnly, T0);
    }

    private ExecutionFill fill(String id, String quantity, String price, String fee, Instant filledAt) {
        return new ExecutionFill(id, new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(fee), "USDT", filledAt);
    }
}
