package com.aiprovider.quant.execution.simulation;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultSimulatedMarketOrderEngineTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final SimulatedExecutionPolicy NO_SLIPPAGE =
            new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", BigDecimal.ZERO);

    private final ExecutionOrderStateMachine stateMachine = new ExecutionOrderStateMachine();
    private final DefaultSimulatedMarketOrderEngine engine = new DefaultSimulatedMarketOrderEngine(stateMachine);

    @Test
    void submitsAcceptedMarketOrderWithDeterministicExecutionOrderId() {
        ExecutionOrderSnapshot accepted = accepted(OrderSide.BUY, "2");

        ExecutionOrderSnapshot submitted = engine.submit(accepted, T0.plusSeconds(2));

        assertThat(submitted.getStatus()).isEqualTo(ExecutionOrderStatus.SUBMITTED);
        assertThat(submitted.getExecutionOrderId()).isEqualTo("SIM-ORDER:client-1");
        assertThat(submitted.getSubmittedAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void buyUsesAskAndAppliesPositiveSlippageAndFee() {
        ExecutionOrderSnapshot submitted = submitted(OrderSide.BUY, "2");
        SimulatedExecutionPolicy policy =
                new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", new BigDecimal("100"));

        SimulatedExecutionResult result = engine.execute(submitted,
                book(T0.plusSeconds(3), "99", "9", "100", "2"), policy);

        assertThat(result.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(result.getOriginalAskPrice()).isEqualByComparingTo("100");
        assertThat(result.getFill().getPrice()).isEqualByComparingTo("101");
        assertThat(result.getFill().getFee()).isEqualByComparingTo("0.202");
        assertThat(result.getFill().getFeeAsset()).isEqualTo("USDT");
    }

    @Test
    void sellUsesBidAndAppliesNegativeSlippage() {
        ExecutionOrderSnapshot submitted = submitted(OrderSide.SELL, "2");
        SimulatedExecutionPolicy policy =
                new SimulatedExecutionPolicy(BigDecimal.ZERO, "USDT", new BigDecimal("100"));

        SimulatedExecutionResult result = engine.execute(submitted,
                book(T0.plusSeconds(3), "100", "2", "101", "9"), policy);

        assertThat(result.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(result.getOriginalBidPrice()).isEqualByComparingTo("100");
        assertThat(result.getFill().getPrice()).isEqualByComparingTo("99");
        assertThat(result.getAvailableTopQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void sufficientLiquidityFillsOrderThroughExistingStateMachine() {
        ExecutionOrderSnapshot submitted = submitted(OrderSide.BUY, "2");

        SimulatedExecutionResult result = engine.execute(submitted,
                book(T0.plusSeconds(3), "99", "9", "100", "3"), NO_SLIPPAGE);

        assertThat(result.isCompletelyFilled()).isTrue();
        assertThat(result.getFillQuantity()).isEqualByComparingTo("2");
        assertThat(result.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(result.getOrderSnapshot().getStatus()).isEqualTo(ExecutionOrderStatus.FILLED);
        assertThat(result.getOrderSnapshot().getFills()).containsExactly(result.getFill());
        assertThat(result.getOrderSnapshot().getFilledQuantity()).isEqualByComparingTo("2");
        assertThat(result.getOrderSnapshot().getAveragePrice()).isEqualByComparingTo("100");
        assertThat(result.getOrderSnapshot().getCompletedAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void insufficientLiquidityPartiallyFillsThenLaterBookCompletesRemainingOrder() {
        ExecutionOrderSnapshot submitted = submitted(OrderSide.BUY, "5");

        SimulatedExecutionResult first = engine.execute(submitted,
                book(T0.plusSeconds(3), "99", "9", "100", "2"), NO_SLIPPAGE);
        SimulatedExecutionResult second = engine.execute(first.getOrderSnapshot(),
                book(T0.plusSeconds(4), "109", "9", "110", "3"), NO_SLIPPAGE);

        assertThat(first.isCompletelyFilled()).isFalse();
        assertThat(first.getOrderSnapshot().getStatus()).isEqualTo(ExecutionOrderStatus.PARTIALLY_FILLED);
        assertThat(first.getFillQuantity()).isEqualByComparingTo("2");
        assertThat(first.getRemainingQuantity()).isEqualByComparingTo("3");
        assertThat(second.isCompletelyFilled()).isTrue();
        assertThat(second.getFillQuantity()).isEqualByComparingTo("3");
        assertThat(second.getRemainingQuantity()).isEqualByComparingTo("0");
        assertThat(second.getOrderSnapshot().getFills()).hasSize(2);
        assertThat(second.getOrderSnapshot().getAveragePrice()).isEqualByComparingTo("106");
    }

    @Test
    void sameBookTimeCannotBeConsumedAgain() {
        Instant bookTime = T0.plusSeconds(3);
        SimulatedExecutionResult partial = engine.execute(submitted(OrderSide.BUY, "3"),
                book(bookTime, "99", "9", "100", "1"), NO_SLIPPAGE);

        assertError("SIMULATED_EXECUTION_TIME_INVALID",
                () -> engine.execute(partial.getOrderSnapshot(),
                        book(bookTime, "99", "9", "100", "1"), NO_SLIPPAGE));
    }

    @Test
    void rejectsOrderAndBookContextMismatch() {
        ExecutionOrderSnapshot symbolMismatch = submitted(
                accepted(OrderSide.BUY, "1", MarketProviderId.BINANCE_USDM,
                        MarketType.USDM_PERPETUAL, "ETHUSDT"));
        assertError("SIMULATED_EXECUTION_CONTEXT_MISMATCH",
                () -> engine.execute(symbolMismatch, book(T0.plusSeconds(3), "99", "1", "100", "1"),
                        NO_SLIPPAGE));
    }

    @Test
    void rejectsStatusesOtherThanSubmittedAndPartiallyFilled() {
        ExecutionOrderSnapshot accepted = accepted(OrderSide.BUY, "1");
        assertError("SIMULATED_EXECUTION_STATUS_INVALID",
                () -> engine.execute(accepted, book(T0.plusSeconds(3), "99", "1", "100", "1"),
                        NO_SLIPPAGE));

        SimulatedExecutionResult filled = engine.execute(engine.submit(accepted, T0.plusSeconds(2)),
                book(T0.plusSeconds(3), "99", "1", "100", "1"), NO_SLIPPAGE);
        assertError("SIMULATED_EXECUTION_STATUS_INVALID",
                () -> engine.execute(filled.getOrderSnapshot(),
                        book(T0.plusSeconds(4), "99", "1", "100", "1"), NO_SLIPPAGE));
    }

    @Test
    void generatesDeterministicFillIdAndIdenticalResults() {
        ExecutionOrderSnapshot firstInput = submitted(OrderSide.BUY, "2");
        ExecutionOrderSnapshot secondInput = submitted(OrderSide.BUY, "2");
        SimulatedTopOfBook top = book(T0.plusSeconds(3), "99", "9", "100", "2");

        SimulatedExecutionResult first = engine.execute(firstInput, top, NO_SLIPPAGE);
        SimulatedExecutionResult second = engine.execute(secondInput, top, NO_SLIPPAGE);

        assertThat(first.getFill().getFillId())
                .isEqualTo("SIM-FILL:SIM-ORDER:client-1:0:" + T0.plusSeconds(3).toEpochMilli());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void topOfBookFactoryCopiesEventFieldsIntoImmutableSnapshot() {
        StreamBookTickerEvent event = event(T0.plusSeconds(3), "99", "1", "100", "2");

        SimulatedTopOfBook snapshot = SimulatedTopOfBook.from(event);
        event.setSymbol("ETHUSDT");
        event.setEventTime(T0.plusSeconds(9));
        event.setBidPrice(new BigDecimal("1"));
        event.setAskQuantity(new BigDecimal("99"));

        assertThat(snapshot.getSymbol()).isEqualTo("BTCUSDT");
        assertThat(snapshot.getEventTime()).isEqualTo(T0.plusSeconds(3));
        assertThat(snapshot.getBidPrice()).isEqualByComparingTo("99");
        assertThat(snapshot.getAskQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void validatesPolicyAndTopOfBookBoundariesWithoutDefaults() {
        assertError("SIMULATED_EXECUTION_POLICY_INVALID",
                () -> new SimulatedExecutionPolicy(null, "USDT", BigDecimal.ZERO));
        assertError("SIMULATED_EXECUTION_POLICY_INVALID",
                () -> new SimulatedExecutionPolicy(new BigDecimal("0.0101"), "USDT", BigDecimal.ZERO));
        assertError("SIMULATED_EXECUTION_POLICY_INVALID",
                () -> new SimulatedExecutionPolicy(BigDecimal.ZERO, " ", BigDecimal.ZERO));
        assertError("SIMULATED_EXECUTION_POLICY_INVALID",
                () -> new SimulatedExecutionPolicy(BigDecimal.ZERO, "USDT", new BigDecimal("1000.1")));
        assertError("SIMULATED_EXECUTION_PRICE_INVALID",
                () -> book(T0.plusSeconds(3), "101", "1", "100", "1"));
        assertError("SIMULATED_EXECUTION_LIQUIDITY_INVALID",
                () -> book(T0.plusSeconds(3), "99", "0", "100", "1"));
        assertError("SIMULATED_EXECUTION_MARKET_INVALID",
                () -> new SimulatedTopOfBook(null, MarketType.USDM_PERPETUAL, "BTCUSDT",
                        T0.plusSeconds(3), new BigDecimal("99"), BigDecimal.ONE,
                        new BigDecimal("100"), BigDecimal.ONE));
        assertError("SIMULATED_EXECUTION_MARKET_INVALID",
                () -> new SimulatedTopOfBook(MarketProviderId.BINANCE_USDM, null, "BTCUSDT",
                        T0.plusSeconds(3), new BigDecimal("99"), BigDecimal.ONE,
                        new BigDecimal("100"), BigDecimal.ONE));
    }

    private ExecutionOrderSnapshot submitted(OrderSide side, String quantity) {
        return submitted(accepted(side, quantity));
    }

    private ExecutionOrderSnapshot submitted(ExecutionOrderSnapshot accepted) {
        return engine.submit(accepted, T0.plusSeconds(2));
    }

    private ExecutionOrderSnapshot accepted(OrderSide side, String quantity) {
        return accepted(side, quantity, MarketProviderId.BINANCE_USDM, MarketType.USDM_PERPETUAL, "BTCUSDT");
    }

    private ExecutionOrderSnapshot accepted(OrderSide side, String quantity, MarketProviderId provider,
                                            MarketType marketType, String symbol) {
        ExecutionOrderRequest request = new ExecutionOrderRequest("client-1", provider, marketType, symbol,
                ExecutionOrderType.MARKET, side, PositionSide.LONG, new BigDecimal(quantity),
                side == OrderSide.SELL, T0);
        return stateMachine.accept(stateMachine.create(request), T0.plusSeconds(1));
    }

    private SimulatedTopOfBook book(Instant eventTime, String bidPrice, String bidQuantity,
                                    String askPrice, String askQuantity) {
        return SimulatedTopOfBook.from(event(eventTime, bidPrice, bidQuantity, askPrice, askQuantity));
    }

    private StreamBookTickerEvent event(Instant eventTime, String bidPrice, String bidQuantity,
                                        String askPrice, String askQuantity) {
        StreamBookTickerEvent event = new StreamBookTickerEvent();
        event.setProvider(MarketProviderId.BINANCE_USDM);
        event.setMarketType(MarketType.USDM_PERPETUAL);
        event.setSymbol("BTCUSDT");
        event.setEventTime(eventTime);
        event.setBidPrice(new BigDecimal(bidPrice));
        event.setBidQuantity(new BigDecimal(bidQuantity));
        event.setAskPrice(new BigDecimal(askPrice));
        event.setAskQuantity(new BigDecimal(askQuantity));
        return event;
    }

    private void assertError(String code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SimulatedExecutionException.class)
                .extracting(error -> ((SimulatedExecutionException) error).getErrorCode())
                .isEqualTo(code);
    }
}
