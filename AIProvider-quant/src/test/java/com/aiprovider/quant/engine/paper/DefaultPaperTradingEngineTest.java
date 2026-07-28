package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.execution.simulation.DefaultSimulatedMarketOrderEngine;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionPolicy;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.portfolio.sizing.DefaultPositionSizingEngine;
import com.aiprovider.quant.portfolio.sizing.MarketOrderQuantityRules;
import com.aiprovider.quant.portfolio.sizing.PositionSizingEngine;
import com.aiprovider.quant.portfolio.sizing.PositionSizingPolicyType;
import com.aiprovider.quant.portfolio.sizing.PositionSizingRequest;
import com.aiprovider.quant.portfolio.sizing.PositionSizingResult;
import com.aiprovider.quant.risk.pretrade.DefaultPreTradeRiskEngine;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskPolicy;
import com.aiprovider.quant.strategy.runtime.StrategyRuntimePosition;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecisionReason;
import com.aiprovider.quant.strategy.runtime.StrategySignalEngine;
import com.aiprovider.quant.strategy.runtime.StrategySignalRequest;
import com.aiprovider.quant.strategy.runtime.StrategySignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaperTradingEngineTest {
    private static final MarketProviderId PROVIDER = MarketProviderId.BINANCE_USDM;
    private static final MarketType MARKET_TYPE = MarketType.USDM_PERPETUAL;
    private static final String SYMBOL = "BTCUSDT";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private PaperAccountEngine accountEngine;
    private SignalStub signalEngine;
    private CountingSizingEngine sizingEngine;

    @BeforeEach
    void setUp() {
        accountEngine = new DefaultPaperAccountEngine();
        signalEngine = new SignalStub();
        sizingEngine = new CountingSizingEngine();
    }

    @Test
    void initializesSessionAndRejectsConfigurationOrAccountMismatch() {
        PaperAccountSnapshot account = flatAccount();
        PaperTradingSessionConfig config = config(approvedPolicy());

        PaperTradingSessionSnapshot session = engine().createSession(config, account);

        assertThat(session.getConfig()).isSameAs(config);
        assertThat(session.getPaperAccountSnapshot()).isSameAs(account);
        assertThat(session.getPendingOrderSnapshot()).isNull();
        assertThat(session.getLastUpdatedAt()).isEqualTo(account.getLastUpdatedAt());
        assertThatThrownBy(() -> new PaperTradingSessionConfig(
                "session-1", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "TEST", "1", Map.of(), PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                new BigDecimal("2.5"), null, quantityRules("ETHUSDT"), BigDecimal.ONE,
                approvedPolicy(), executionPolicy()))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_CONFIG_INVALID);

        PaperAccountSnapshot wrongAsset = accountEngine.initialize(
                "account-2", PROVIDER, MARKET_TYPE, "BUSD", new BigDecimal("10000"),
                LocalDate.of(2026, 1, 1), START);
        assertThatThrownBy(() -> engine().createSession(config, wrongAsset))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_CONTEXT_MISMATCH);
    }

    @Test
    void flatHoldMarksAccountAndDoesNotCreateOrder() {
        signalEngine.signalType = StrategySignalType.HOLD;
        PaperTradingSessionSnapshot session = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle candle = candle(1, "100");

        PaperTradingStepResult result = engine().evaluateClosedCandles(
                session, List.of(candle), evaluatedAt(candle));

        assertThat(result.getStepType()).isEqualTo(PaperTradingStepType.SIGNAL_HOLD);
        assertThat(result.getSignalDecision().getSignalType()).isEqualTo(StrategySignalType.HOLD);
        assertThat(result.getPositionSizingResult()).isNull();
        assertThat(result.getPreTradeRiskDecision()).isNull();
        assertThat(result.getExecutionOrderSnapshot()).isNull();
        assertThat(result.getSession().getPaperAccountSnapshot().getLastUpdatedAt())
                .isEqualTo(evaluatedAt(candle));
        assertThat(sizingEngine.calls).isZero();
    }

    @Test
    void enterLongSizesApprovesAndSubmitsDeterministicOrder() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionSnapshot session = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle candle = candle(1, "100");

        PaperTradingStepResult result = engine().evaluateClosedCandles(
                session, List.of(candle), evaluatedAt(candle));

        assertThat(result.getStepType()).isEqualTo(PaperTradingStepType.ENTRY_ORDER_SUBMITTED);
        assertThat(result.getPositionSizingResult().normalizedQuantity())
                .isEqualByComparingTo("2.5");
        assertThat(result.getPreTradeRiskDecision().getViolations()).isEmpty();
        assertThat(result.getExecutionOrderSnapshot().getStatus())
                .isEqualTo(ExecutionOrderStatus.SUBMITTED);
        assertThat(result.getExecutionOrderSnapshot().getRequest().getQuantity())
                .isEqualByComparingTo("2.5");
        assertThat(result.getExecutionOrderSnapshot().getRequest().getClientOrderId())
                .isEqualTo("PAPER:session-1:" + candle.getCloseTime().toEpochMilli() + ":ENTRY");
        assertThat(result.getSession().getPendingOrderSnapshot())
                .isEqualTo(result.getExecutionOrderSnapshot());
        assertThat(sizingEngine.calls).isOne();
    }

    @Test
    void rejectedRiskProducesRejectedOrderAndNoPendingOrder() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionSnapshot session = engine().createSession(config(rejectingPolicy()), flatAccount());
        HistoricalCandle candle = candle(1, "100");

        PaperTradingStepResult result = engine().evaluateClosedCandles(
                session, List.of(candle), evaluatedAt(candle));

        assertThat(result.getStepType()).isEqualTo(PaperTradingStepType.RISK_REJECTED);
        assertThat(result.getExecutionOrderSnapshot().getStatus())
                .isEqualTo(ExecutionOrderStatus.REJECTED);
        assertThat(result.getExecutionOrderSnapshot().getTerminalErrorCode())
                .isEqualTo("PRE_TRADE_RISK_REJECTED");
        assertThat(result.getExecutionOrderSnapshot().getTerminalErrorMessage())
                .isEqualTo("ORDER_NOTIONAL_LIMIT_EXCEEDED");
        assertThat(result.getSession().getPendingOrderSnapshot()).isNull();
        assertThat(result.getSession().getLastOrderSnapshot())
                .isEqualTo(result.getExecutionOrderSnapshot());
    }

    @Test
    void partialThenCompleteFillUpdatesAccountAndPendingOrderTogether() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionSnapshot initial = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle candle = candle(1, "100");
        PaperTradingSessionSnapshot submitted = engine().evaluateClosedCandles(
                initial, List.of(candle), evaluatedAt(candle)).getSession();

        SimulatedTopOfBook firstBook = book(
                submitted.getLastUpdatedAt().plusSeconds(1), "99", "100", "1");
        PaperTradingStepResult partial = engine().executePendingOrder(submitted, firstBook);

        assertThat(partial.getStepType()).isEqualTo(PaperTradingStepType.ORDER_PARTIALLY_FILLED);
        assertThat(partial.getExecutionOrderSnapshot().getFilledQuantity()).isEqualByComparingTo("1");
        assertThat(partial.getExecutionOrderSnapshot().getRemainingQuantity()).isEqualByComparingTo("1.5");
        assertThat(partial.getSession().getPendingOrderSnapshot())
                .isEqualTo(partial.getExecutionOrderSnapshot());
        assertThat(partial.getSession().getPaperAccountSnapshot().getPosition().getQuantity())
                .isEqualByComparingTo("1");

        SimulatedTopOfBook secondBook = book(
                firstBook.getEventTime().plusSeconds(1), "100", "101", "10");
        PaperTradingStepResult filled = engine().executePendingOrder(partial.getSession(), secondBook);

        assertThat(filled.getStepType()).isEqualTo(PaperTradingStepType.ORDER_FILLED);
        assertThat(filled.getExecutionOrderSnapshot().getFilledQuantity()).isEqualByComparingTo("2.5");
        assertThat(filled.getExecutionOrderSnapshot().getRemainingQuantity()).isZero();
        assertThat(filled.getSession().getPendingOrderSnapshot()).isNull();
        assertThat(filled.getSession().getPaperAccountSnapshot().getPosition().getQuantity())
                .isEqualByComparingTo("2.5");
        assertThat(filled.getSession().getPaperAccountSnapshot().getAppliedFills()).hasSize(2);
        assertThat(filled.getSession().getPaperAccountSnapshot().getPosition().getQuantity())
                .isEqualByComparingTo(filled.getExecutionOrderSnapshot().getFilledQuantity());
    }

    @Test
    void exitUsesEntireOpenPositionWithoutSizingAndCanExitAboveDailyLossLimit() {
        signalEngine.signalType = StrategySignalType.EXIT_LONG;
        PaperAccountSnapshot account = longAccount("1.75", "100", "0.10");
        PaperTradingSessionSnapshot session = engine().createSession(
                config(new PreTradeRiskPolicy(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        new BigDecimal("0.000001"), 1)), account);
        HistoricalCandle candle = candle(2, "95");

        PaperTradingStepResult result = engine().evaluateClosedCandles(
                session, List.of(candle), evaluatedAt(candle));

        assertThat(result.getStepType()).isEqualTo(PaperTradingStepType.EXIT_ORDER_SUBMITTED);
        assertThat(result.getExecutionOrderSnapshot().getRequest().getOrderSide())
                .isEqualTo(OrderSide.SELL);
        assertThat(result.getExecutionOrderSnapshot().getRequest().isReduceOnly()).isTrue();
        assertThat(result.getExecutionOrderSnapshot().getRequest().getQuantity())
                .isEqualByComparingTo("1.75");
        assertThat(result.getPositionSizingResult()).isNull();
        assertThat(result.getPreTradeRiskDecision().getViolations()).isEmpty();
        assertThat(sizingEngine.calls).isZero();
    }

    @Test
    void activePendingOrderMarksNewCandleWithoutCallingSignalOrCreatingSecondOrder() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionSnapshot initial = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle first = candle(1, "100");
        PaperTradingSessionSnapshot submitted = engine().evaluateClosedCandles(
                initial, List.of(first), evaluatedAt(first)).getSession();
        int signalCalls = signalEngine.calls;
        int sizingCalls = sizingEngine.calls;
        HistoricalCandle second = candle(2, "105");

        PaperTradingStepResult result = engine().evaluateClosedCandles(
                submitted, List.of(second), evaluatedAt(second));

        assertThat(result.getStepType()).isEqualTo(PaperTradingStepType.PENDING_ORDER_ACTIVE);
        assertThat(result.getSession().getPendingOrderSnapshot())
                .isEqualTo(submitted.getPendingOrderSnapshot());
        assertThat(result.getSession().getLastEvaluatedCandle().getClosePrice())
                .isEqualByComparingTo("105");
        assertThat(signalEngine.calls).isEqualTo(signalCalls);
        assertThat(sizingEngine.calls).isEqualTo(sizingCalls);
    }

    @Test
    void duplicateCandleIsIdempotentButConflictingOrOlderCandleFails() {
        signalEngine.signalType = StrategySignalType.HOLD;
        PaperTradingSessionSnapshot initial = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle first = candle(2, "100");
        PaperTradingSessionSnapshot evaluated = engine().evaluateClosedCandles(
                initial, List.of(first), evaluatedAt(first)).getSession();

        PaperTradingStepResult duplicate = engine().evaluateClosedCandles(
                evaluated, List.of(copy(first, "100")), evaluated.getLastUpdatedAt());
        assertThat(duplicate.getStepType())
                .isEqualTo(PaperTradingStepType.DUPLICATE_CANDLE_IGNORED);
        assertThat(duplicate.getSession()).isSameAs(evaluated);

        assertThatThrownBy(() -> engine().evaluateClosedCandles(
                evaluated, List.of(copy(first, "101")), evaluated.getLastUpdatedAt()))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_CANDLE_CONFLICT);

        HistoricalCandle older = candle(1, "99");
        assertThatThrownBy(() -> engine().evaluateClosedCandles(
                evaluated, List.of(older), evaluated.getLastUpdatedAt()))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_CANDLE_TIME_INVALID);
    }

    @Test
    void topOfBookContextMustMatchAndFilledOrderCannotConsumeMoreMarketData() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionSnapshot initial = engine().createSession(config(approvedPolicy()), flatAccount());
        HistoricalCandle candle = candle(1, "100");
        PaperTradingSessionSnapshot submitted = engine().evaluateClosedCandles(
                initial, List.of(candle), evaluatedAt(candle)).getSession();

        SimulatedTopOfBook wrongSymbol = new SimulatedTopOfBook(
                PROVIDER, MARKET_TYPE, "ETHUSDT", submitted.getLastUpdatedAt().plusSeconds(1),
                new BigDecimal("99"), BigDecimal.TEN, new BigDecimal("100"), BigDecimal.TEN);
        assertThatThrownBy(() -> engine().executePendingOrder(submitted, wrongSymbol))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_CONTEXT_MISMATCH);

        PaperTradingSessionSnapshot filled = engine().executePendingOrder(
                submitted, book(submitted.getLastUpdatedAt().plusSeconds(1), "99", "100", "10"))
                .getSession();
        assertThatThrownBy(() -> engine().executePendingOrder(
                filled, book(filled.getLastUpdatedAt().plusSeconds(1), "99", "100", "10")))
                .isInstanceOf(PaperTradingException.class)
                .extracting("errorCode")
                .isEqualTo(PaperTradingException.PAPER_TRADING_ORDER_NOT_PENDING);
    }

    @Test
    void sameSessionAndInputsProduceIdenticalBusinessValues() {
        signalEngine.signalType = StrategySignalType.ENTER_LONG;
        PaperTradingSessionConfig config = config(approvedPolicy());
        PaperAccountSnapshot account = flatAccount();
        PaperTradingSessionSnapshot firstSession = engine().createSession(config, account);
        PaperTradingSessionSnapshot secondSession = engine().createSession(config, account);
        HistoricalCandle candle = candle(1, "100");

        PaperTradingStepResult first = engine().evaluateClosedCandles(
                firstSession, List.of(candle), evaluatedAt(candle));
        PaperTradingStepResult second = engine().evaluateClosedCandles(
                secondSession, List.of(candle), evaluatedAt(candle));

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
    }

    private DefaultPaperTradingEngine engine() {
        return new DefaultPaperTradingEngine(
                signalEngine, sizingEngine, new DefaultPreTradeRiskEngine(),
                new ExecutionOrderStateMachine(), new DefaultSimulatedMarketOrderEngine(), accountEngine);
    }

    private PaperTradingSessionConfig config(PreTradeRiskPolicy riskPolicy) {
        return new PaperTradingSessionConfig(
                "session-1", PROVIDER, MARKET_TYPE, SYMBOL, KlineInterval.M1,
                "TEST", "1", Map.of("period", 3),
                PositionSizingPolicyType.FIXED_BASE_QUANTITY, new BigDecimal("2.5"), null,
                quantityRules(SYMBOL), BigDecimal.ONE, riskPolicy, executionPolicy());
    }

    private MarketOrderQuantityRules quantityRules(String symbol) {
        return new MarketOrderQuantityRules(
                PROVIDER, MARKET_TYPE, symbol, "USDT", 3, new BigDecimal("0.001"),
                new BigDecimal("0.001"), new BigDecimal("1000"), new BigDecimal("5"));
    }

    private SimulatedExecutionPolicy executionPolicy() {
        return new SimulatedExecutionPolicy(new BigDecimal("0.001"), "USDT", BigDecimal.ZERO);
    }

    private PreTradeRiskPolicy approvedPolicy() {
        return new PreTradeRiskPolicy(
                new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.01"),
                new BigDecimal("0.50"), 5);
    }

    private PreTradeRiskPolicy rejectingPolicy() {
        return new PreTradeRiskPolicy(
                new BigDecimal("0.01"), new BigDecimal("0.90"), BigDecimal.ZERO,
                new BigDecimal("0.50"), 5);
    }

    private PaperAccountSnapshot flatAccount() {
        return accountEngine.initialize(
                "account-1", PROVIDER, MARKET_TYPE, "USDT", new BigDecimal("10000"),
                LocalDate.of(2026, 1, 1), START);
    }

    private PaperAccountSnapshot longAccount(String quantity, String price, String fee) {
        PaperAccountSnapshot account = flatAccount();
        Instant fillTime = START.plusSeconds(1);
        ExecutionOrderRequest request = new ExecutionOrderRequest(
                "OPEN-ACCOUNT", PROVIDER, MARKET_TYPE, SYMBOL, ExecutionOrderType.MARKET,
                OrderSide.BUY, PositionSide.LONG, new BigDecimal(quantity), false, START);
        ExecutionFill fill = new ExecutionFill(
                "OPEN-FILL", new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal(fee), "USDT", fillTime);
        return accountEngine.applyFill(account, request, fill).getAccount();
    }

    private HistoricalCandle candle(int minute, String close) {
        Instant openTime = START.plusSeconds(minute * 60L);
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(PROVIDER);
        candle.setMarketType(MARKET_TYPE);
        candle.setSymbol(SYMBOL);
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(openTime);
        candle.setCloseTime(openTime.plusSeconds(59));
        candle.setOpenPrice(new BigDecimal(close));
        candle.setHighPrice(new BigDecimal(close).add(BigDecimal.ONE));
        candle.setLowPrice(new BigDecimal(close).subtract(BigDecimal.ONE));
        candle.setClosePrice(new BigDecimal(close));
        candle.setVolume(new BigDecimal("10"));
        candle.setQuoteVolume(new BigDecimal("1000"));
        candle.setTradeCount(10);
        return candle;
    }

    private HistoricalCandle copy(HistoricalCandle source, String close) {
        HistoricalCandle result = candle(
                (int) ((source.getOpenTime().getEpochSecond() - START.getEpochSecond()) / 60), close);
        result.setOpenTime(source.getOpenTime());
        result.setCloseTime(source.getCloseTime());
        return result;
    }

    private Instant evaluatedAt(HistoricalCandle candle) {
        return candle.getCloseTime().plusMillis(1);
    }

    private SimulatedTopOfBook book(
            Instant eventTime, String bid, String ask, String quantity) {
        return new SimulatedTopOfBook(
                PROVIDER, MARKET_TYPE, SYMBOL, eventTime,
                new BigDecimal(bid), new BigDecimal(quantity),
                new BigDecimal(ask), new BigDecimal(quantity));
    }

    private static final class SignalStub implements StrategySignalEngine {
        private StrategySignalType signalType = StrategySignalType.HOLD;
        private int calls;

        @Override
        public StrategySignalDecision evaluate(StrategySignalRequest request) {
            calls++;
            HistoricalCandle target = request.getCandles().get(request.getCandles().size() - 1);
            StrategySignalDecisionReason reason = signalType == StrategySignalType.ENTER_LONG
                    ? StrategySignalDecisionReason.ENTRY_RULE_MATCHED
                    : signalType == StrategySignalType.EXIT_LONG
                    ? StrategySignalDecisionReason.EXIT_RULE_MATCHED
                    : request.getCurrentPosition() == StrategyRuntimePosition.FLAT
                    ? StrategySignalDecisionReason.ENTRY_RULE_NOT_MATCHED
                    : StrategySignalDecisionReason.EXIT_RULE_NOT_MATCHED;
            return new StrategySignalDecision(
                    request.getStrategyCode(), request.getStrategyVersion(),
                    request.getStrategyParameters(), request.getProvider(), request.getMarketType(),
                    request.getSymbol(), request.getInterval(), request.getCurrentPosition(),
                    signalType, request.getCandles().size() - 1, target, reason);
        }
    }

    private static final class CountingSizingEngine implements PositionSizingEngine {
        private final PositionSizingEngine delegate = new DefaultPositionSizingEngine();
        private int calls;

        @Override
        public PositionSizingResult calculate(PositionSizingRequest request) {
            calls++;
            return delegate.calculate(request);
        }
    }
}
