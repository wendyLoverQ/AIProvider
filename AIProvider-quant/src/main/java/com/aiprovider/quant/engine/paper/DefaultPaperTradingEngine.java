package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountException;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.account.paper.PaperAccountUpdateResult;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionOrderException;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.simulation.DefaultSimulatedMarketOrderEngine;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionException;
import com.aiprovider.quant.execution.simulation.SimulatedExecutionResult;
import com.aiprovider.quant.execution.simulation.SimulatedMarketOrderEngine;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.portfolio.sizing.DefaultPositionSizingEngine;
import com.aiprovider.quant.portfolio.sizing.PositionSizingEngine;
import com.aiprovider.quant.portfolio.sizing.PositionSizingException;
import com.aiprovider.quant.portfolio.sizing.PositionSizingRequest;
import com.aiprovider.quant.portfolio.sizing.PositionSizingResult;
import com.aiprovider.quant.risk.pretrade.DefaultPreTradeRiskEngine;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskContext;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecision;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskDecisionStatus;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskEngine;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskException;
import com.aiprovider.quant.risk.pretrade.PreTradeRiskViolation;
import com.aiprovider.quant.strategy.runtime.StrategyRuntimePosition;
import com.aiprovider.quant.strategy.runtime.StrategySignalDecision;
import com.aiprovider.quant.strategy.runtime.StrategySignalEngine;
import com.aiprovider.quant.strategy.runtime.StrategySignalException;
import com.aiprovider.quant.strategy.runtime.StrategySignalRequest;
import com.aiprovider.quant.strategy.runtime.StrategySignalType;
import com.aiprovider.quant.strategy.runtime.Ta4jStrategySignalEngine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class DefaultPaperTradingEngine implements PaperTradingEngine {
    private static final String PRE_TRADE_RISK_REJECTED = "PRE_TRADE_RISK_REJECTED";

    private final StrategySignalEngine strategySignalEngine;
    private final PositionSizingEngine positionSizingEngine;
    private final PreTradeRiskEngine preTradeRiskEngine;
    private final ExecutionOrderStateMachine orderStateMachine;
    private final SimulatedMarketOrderEngine simulatedMarketOrderEngine;
    private final PaperAccountEngine paperAccountEngine;

    public DefaultPaperTradingEngine() {
        this(new Ta4jStrategySignalEngine(), new DefaultPositionSizingEngine(),
                new DefaultPreTradeRiskEngine(), new ExecutionOrderStateMachine(),
                new DefaultSimulatedMarketOrderEngine(), new DefaultPaperAccountEngine());
    }

    public DefaultPaperTradingEngine(
            StrategySignalEngine strategySignalEngine,
            PositionSizingEngine positionSizingEngine,
            PreTradeRiskEngine preTradeRiskEngine,
            ExecutionOrderStateMachine orderStateMachine,
            SimulatedMarketOrderEngine simulatedMarketOrderEngine,
            PaperAccountEngine paperAccountEngine) {
        if (strategySignalEngine == null || positionSizingEngine == null || preTradeRiskEngine == null
                || orderStateMachine == null || simulatedMarketOrderEngine == null
                || paperAccountEngine == null) {
            throw requestInvalid("all paper trading dependencies are required");
        }
        this.strategySignalEngine = strategySignalEngine;
        this.positionSizingEngine = positionSizingEngine;
        this.preTradeRiskEngine = preTradeRiskEngine;
        this.orderStateMachine = orderStateMachine;
        this.simulatedMarketOrderEngine = simulatedMarketOrderEngine;
        this.paperAccountEngine = paperAccountEngine;
    }

    @Override
    public PaperTradingSessionSnapshot createSession(
            PaperTradingSessionConfig config, PaperAccountSnapshot paperAccount) {
        if (config == null || paperAccount == null) {
            throw requestInvalid("config and paperAccount are required");
        }
        if (paperAccount.getProvider() != config.getProvider()
                || paperAccount.getMarketType() != config.getMarketType()) {
            throw contextMismatch("account provider or marketType does not match session config");
        }
        if (!paperAccount.getQuoteAsset().equals(
                config.getSimulatedExecutionPolicy().getFeeAsset())) {
            throw contextMismatch("account quoteAsset does not match execution feeAsset");
        }
        if (paperAccount.getPosition().isOpen()
                && !config.getSymbol().equals(paperAccount.getPosition().getSymbol())) {
            throw contextMismatch("open account position symbol does not match session config");
        }
        return new PaperTradingSessionSnapshot(
                config, paperAccount, null, null, null, null, null, null,
                paperAccount.getLastUpdatedAt());
    }

    @Override
    public PaperTradingStepResult evaluateClosedCandles(
            PaperTradingSessionSnapshot session,
            List<HistoricalCandle> candles,
            Instant evaluatedAt) {
        requireSession(session);
        if (candles == null || candles.isEmpty() || evaluatedAt == null) {
            throw requestInvalid("session, non-empty candles and evaluatedAt are required");
        }
        PaperTradingSessionConfig config = session.getConfig();
        for (HistoricalCandle candle : candles) {
            PaperSignalCandleSnapshot snapshot = PaperSignalCandleSnapshot.from(candle);
            requireCandleContext(config, snapshot);
        }
        List<HistoricalCandle> immutableCandles = List.copyOf(candles);
        HistoricalCandle targetCandle = immutableCandles.get(immutableCandles.size() - 1);
        PaperSignalCandleSnapshot target = PaperSignalCandleSnapshot.from(targetCandle);
        if (evaluatedAt.isBefore(target.getCloseTime())
                || evaluatedAt.isBefore(session.getLastUpdatedAt())) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_CANDLE_TIME_INVALID,
                    "evaluatedAt must not precede candle closeTime or session lastUpdatedAt");
        }
        PaperTradingStepResult replay = handleReplay(session, target);
        if (replay != null) {
            return replay;
        }

        PaperAccountSnapshot markedAccount = markToMarket(
                session.getPaperAccountSnapshot(), config.getSymbol(), target.getClosePrice(), evaluatedAt);
        if (session.getPendingOrderSnapshot() != null) {
            PaperTradingSessionSnapshot updated = nextSession(
                    session, markedAccount, session.getPendingOrderSnapshot(),
                    session.getLastOrderSnapshot(), session.getLastSignalDecision(),
                    session.getLastSizingResult(), session.getLastRiskDecision(), target, evaluatedAt);
            return result(PaperTradingStepType.PENDING_ORDER_ACTIVE, updated, null, null, null,
                    session.getPendingOrderSnapshot(), null, null);
        }

        StrategyRuntimePosition runtimePosition = markedAccount.getPosition().isOpen()
                ? StrategyRuntimePosition.LONG : StrategyRuntimePosition.FLAT;
        StrategySignalDecision signal = evaluateSignal(config, immutableCandles, runtimePosition);
        requireSignalMatchesTarget(signal, config, runtimePosition, target);

        if (signal.getSignalType() == StrategySignalType.HOLD) {
            PaperTradingSessionSnapshot updated = nextSession(
                    session, markedAccount, null, session.getLastOrderSnapshot(), signal,
                    null, null, target, evaluatedAt);
            return result(PaperTradingStepType.SIGNAL_HOLD, updated, signal, null, null,
                    null, null, null);
        }

        PositionSizingResult sizing = null;
        BigDecimal quantity;
        OrderSide orderSide;
        boolean reduceOnly;
        String orderSuffix;
        if (signal.getSignalType() == StrategySignalType.ENTER_LONG) {
            if (markedAccount.getPosition().isOpen()) {
                throw stateInvalid("ENTER_LONG requires a flat account");
            }
            sizing = calculateSizing(config, markedAccount, signal);
            quantity = sizing.normalizedQuantity();
            orderSide = OrderSide.BUY;
            reduceOnly = false;
            orderSuffix = "ENTRY";
        } else if (signal.getSignalType() == StrategySignalType.EXIT_LONG) {
            if (markedAccount.getPosition().isFlat()) {
                throw stateInvalid("EXIT_LONG requires an open LONG position");
            }
            quantity = markedAccount.getPosition().getQuantity();
            orderSide = OrderSide.SELL;
            reduceOnly = true;
            orderSuffix = "EXIT";
        } else {
            throw stateInvalid("unsupported signal type: " + signal.getSignalType());
        }

        String clientOrderId = "PAPER:" + config.getSessionId() + ":"
                + signal.getSignalCloseTime().toEpochMilli() + ":" + orderSuffix;
        ExecutionOrderRequest request;
        ExecutionOrderSnapshot created;
        try {
            request = new ExecutionOrderRequest(
                    clientOrderId, config.getProvider(), config.getMarketType(), config.getSymbol(),
                    config.getOrderType(), orderSide, PositionSide.LONG, quantity, reduceOnly, evaluatedAt);
            created = orderStateMachine.create(request);
        } catch (ExecutionOrderException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_ORDER_FAILED, exception);
        }

        PreTradeRiskDecision risk = evaluateRisk(config, markedAccount, signal, request);
        if (risk.getDecisionStatus() == PreTradeRiskDecisionStatus.REJECTED) {
            String violationCodes = risk.getViolations().stream()
                    .map(PreTradeRiskViolation::getCode)
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
            ExecutionOrderSnapshot rejected;
            try {
                rejected = orderStateMachine.reject(
                        created, PRE_TRADE_RISK_REJECTED, violationCodes, evaluatedAt);
            } catch (ExecutionOrderException exception) {
                throw lowerFailure(PaperTradingException.PAPER_TRADING_ORDER_FAILED, exception);
            }
            PaperTradingSessionSnapshot updated = nextSession(
                    session, markedAccount, null, rejected, signal, sizing, risk, target, evaluatedAt);
            return result(PaperTradingStepType.RISK_REJECTED, updated, signal, sizing, risk,
                    rejected, null, null);
        }
        if (risk.getDecisionStatus() != PreTradeRiskDecisionStatus.APPROVED) {
            throw stateInvalid("unsupported pre-trade risk decision status");
        }

        ExecutionOrderSnapshot submitted;
        try {
            ExecutionOrderSnapshot accepted = orderStateMachine.accept(created, evaluatedAt);
            submitted = simulatedMarketOrderEngine.submit(accepted, evaluatedAt);
        } catch (ExecutionOrderException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_ORDER_FAILED, exception);
        } catch (SimulatedExecutionException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_EXECUTION_FAILED, exception);
        }
        if (submitted.getStatus() != ExecutionOrderStatus.SUBMITTED) {
            throw stateInvalid("simulated submit did not produce SUBMITTED order");
        }
        PaperTradingSessionSnapshot updated = nextSession(
                session, markedAccount, submitted, submitted, signal, sizing, risk, target, evaluatedAt);
        PaperTradingStepType stepType = signal.getSignalType() == StrategySignalType.ENTER_LONG
                ? PaperTradingStepType.ENTRY_ORDER_SUBMITTED
                : PaperTradingStepType.EXIT_ORDER_SUBMITTED;
        return result(stepType, updated, signal, sizing, risk, submitted, null, null);
    }

    @Override
    public PaperTradingStepResult executePendingOrder(
            PaperTradingSessionSnapshot session, SimulatedTopOfBook topOfBook) {
        requireSession(session);
        if (topOfBook == null) {
            throw requestInvalid("topOfBook is required");
        }
        ExecutionOrderSnapshot pending = session.getPendingOrderSnapshot();
        if (pending == null
                || (pending.getStatus() != ExecutionOrderStatus.SUBMITTED
                && pending.getStatus() != ExecutionOrderStatus.PARTIALLY_FILLED)) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_ORDER_NOT_PENDING,
                    "session has no SUBMITTED or PARTIALLY_FILLED pending order");
        }
        PaperTradingSessionConfig config = session.getConfig();
        if (topOfBook.getProvider() != config.getProvider()
                || topOfBook.getMarketType() != config.getMarketType()
                || !topOfBook.getSymbol().equals(config.getSymbol())) {
            throw contextMismatch("top-of-book context does not match session config");
        }
        if (!topOfBook.getEventTime().isAfter(pending.getLastUpdatedAt())) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_CANDLE_TIME_INVALID,
                    "top-of-book eventTime must be later than the pending order event");
        }

        SimulatedExecutionResult executionResult;
        try {
            executionResult = simulatedMarketOrderEngine.execute(
                    pending, topOfBook, config.getSimulatedExecutionPolicy());
        } catch (SimulatedExecutionException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_EXECUTION_FAILED, exception);
        }
        ExecutionOrderSnapshot updatedOrder = executionResult.getOrderSnapshot();
        if (!Objects.equals(updatedOrder.getRequest(), pending.getRequest())
                || (updatedOrder.getStatus() != ExecutionOrderStatus.PARTIALLY_FILLED
                && updatedOrder.getStatus() != ExecutionOrderStatus.FILLED)) {
            throw stateInvalid("simulated execution returned an inconsistent order");
        }

        PaperAccountUpdateResult accountUpdate;
        try {
            accountUpdate = paperAccountEngine.applyFill(
                    session.getPaperAccountSnapshot(), updatedOrder.getRequest(),
                    executionResult.getFill());
        } catch (PaperAccountException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_ACCOUNT_FAILED, exception);
        }
        if (!accountUpdate.isApplied()) {
            throw stateInvalid("new simulated fill was not applied to the paper account");
        }

        boolean filled = updatedOrder.getStatus() == ExecutionOrderStatus.FILLED;
        PaperTradingSessionSnapshot updatedSession = nextSession(
                session, accountUpdate.getAccount(), filled ? null : updatedOrder, updatedOrder,
                session.getLastSignalDecision(), session.getLastSizingResult(),
                session.getLastRiskDecision(), session.getLastEvaluatedCandle(),
                accountUpdate.getAccount().getLastUpdatedAt());
        return result(
                filled ? PaperTradingStepType.ORDER_FILLED
                        : PaperTradingStepType.ORDER_PARTIALLY_FILLED,
                updatedSession, null, null, null, updatedOrder, executionResult, accountUpdate);
    }

    private PaperTradingStepResult handleReplay(
            PaperTradingSessionSnapshot session, PaperSignalCandleSnapshot target) {
        PaperSignalCandleSnapshot previous = session.getLastEvaluatedCandle();
        if (previous == null) {
            return null;
        }
        if (previous.equals(target)) {
            return result(PaperTradingStepType.DUPLICATE_CANDLE_IGNORED, session,
                    null, null, null, null, null, null);
        }
        if (previous.getOpenTime().equals(target.getOpenTime())
                || previous.getCloseTime().equals(target.getCloseTime())) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_CANDLE_CONFLICT,
                    "candle time already exists with different content");
        }
        if (!target.getOpenTime().isAfter(previous.getOpenTime())
                || !target.getCloseTime().isAfter(previous.getCloseTime())) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_CANDLE_TIME_INVALID,
                    "new candle must be later than the last evaluated candle");
        }
        return null;
    }

    private PaperAccountSnapshot markToMarket(
            PaperAccountSnapshot account, String symbol, BigDecimal price, Instant markedAt) {
        try {
            return paperAccountEngine.markToMarket(account, symbol, price, markedAt);
        } catch (PaperAccountException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_ACCOUNT_FAILED, exception);
        }
    }

    private StrategySignalDecision evaluateSignal(
            PaperTradingSessionConfig config, List<HistoricalCandle> candles,
            StrategyRuntimePosition position) {
        try {
            return strategySignalEngine.evaluate(new StrategySignalRequest(
                    config.getStrategyCode(), config.getStrategyVersion(),
                    config.getStrategyParameters(), config.getProvider(), config.getMarketType(),
                    config.getSymbol(), config.getKlineInterval(), candles, position));
        } catch (StrategySignalException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_SIGNAL_FAILED, exception);
        } catch (RuntimeException exception) {
            throw new PaperTradingException(
                    PaperTradingException.PAPER_TRADING_SIGNAL_FAILED,
                    "lowerErrorCode=" + exception.getClass().getSimpleName(), exception);
        }
    }

    private PositionSizingResult calculateSizing(
            PaperTradingSessionConfig config, PaperAccountSnapshot account,
            StrategySignalDecision signal) {
        try {
            return positionSizingEngine.calculate(new PositionSizingRequest(
                    config.getProvider(), config.getMarketType(), config.getSymbol(),
                    PositionSide.LONG, config.getPositionSizingPolicyType(),
                    account.getTotalEquity(), account.getAvailableCapital(),
                    account.getPosition().getPositionNotional(), signal.getSignalPrice(),
                    config.getSimulatedExecutionPolicy().getFeeRate(), config.getLeverage(),
                    config.getFixedBaseQuantity(), config.getEquityFraction(),
                    config.getMarketOrderQuantityRules()));
        } catch (PositionSizingException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_SIZING_FAILED, exception);
        }
    }

    private PreTradeRiskDecision evaluateRisk(
            PaperTradingSessionConfig config, PaperAccountSnapshot account,
            StrategySignalDecision signal, ExecutionOrderRequest orderRequest) {
        try {
            PreTradeRiskContext context = new PreTradeRiskContext(
                    config.getProvider(), config.getMarketType(), config.getSymbol(),
                    signal.getSignalPrice(), config.getSimulatedExecutionPolicy().getFeeRate(),
                    account.getTotalEquity(), account.getAvailableCapital(),
                    account.getPosition().getQuantity(), account.getPosition().getPositionNotional(),
                    account.getTradingDayState().getDayStartEquity(),
                    account.getTradingDayState().getDailyRealizedPnl(),
                    account.getConsecutiveLosses());
            return preTradeRiskEngine.evaluate(
                    orderRequest, context, config.getPreTradeRiskPolicy());
        } catch (PreTradeRiskException exception) {
            throw lowerFailure(PaperTradingException.PAPER_TRADING_RISK_FAILED, exception);
        }
    }

    private void requireSignalMatchesTarget(
            StrategySignalDecision signal, PaperTradingSessionConfig config,
            StrategyRuntimePosition position, PaperSignalCandleSnapshot target) {
        if (signal == null || signal.getProvider() != config.getProvider()
                || signal.getMarketType() != config.getMarketType()
                || !config.getSymbol().equals(signal.getSymbol())
                || signal.getInterval() != config.getKlineInterval()
                || signal.getCurrentPosition() != position
                || !target.getOpenTime().equals(signal.getSignalOpenTime())
                || !target.getCloseTime().equals(signal.getSignalCloseTime())
                || !target.getClosePrice().equals(signal.getSignalPrice())) {
            throw stateInvalid("strategy signal does not match session target candle");
        }
    }

    private void requireSession(PaperTradingSessionSnapshot session) {
        if (session == null) {
            throw requestInvalid("session is required");
        }
        PaperTradingSessionConfig config = session.getConfig();
        PaperAccountSnapshot account = session.getPaperAccountSnapshot();
        if (account.getProvider() != config.getProvider()
                || account.getMarketType() != config.getMarketType()
                || !account.getQuoteAsset().equals(
                config.getSimulatedExecutionPolicy().getFeeAsset())) {
            throw stateInvalid("session account context is inconsistent with config");
        }
        if (session.getPendingOrderSnapshot() != null
                && !Objects.equals(session.getPendingOrderSnapshot(),
                session.getLastOrderSnapshot())) {
            throw stateInvalid("pending order must equal last order");
        }
    }

    private void requireCandleContext(
            PaperTradingSessionConfig config, PaperSignalCandleSnapshot candle) {
        if (candle.getProvider() != config.getProvider()
                || candle.getMarketType() != config.getMarketType()
                || !candle.getSymbol().equals(config.getSymbol())
                || candle.getInterval() != config.getKlineInterval()) {
            throw contextMismatch("candle context does not match session config");
        }
    }

    private PaperTradingSessionSnapshot nextSession(
            PaperTradingSessionSnapshot old, PaperAccountSnapshot account,
            ExecutionOrderSnapshot pending, ExecutionOrderSnapshot lastOrder,
            StrategySignalDecision signal, PositionSizingResult sizing,
            PreTradeRiskDecision risk, PaperSignalCandleSnapshot candle, Instant updatedAt) {
        return new PaperTradingSessionSnapshot(
                old.getConfig(), account, pending, lastOrder, signal, sizing, risk, candle, updatedAt);
    }

    private PaperTradingStepResult result(
            PaperTradingStepType type, PaperTradingSessionSnapshot session,
            StrategySignalDecision signal, PositionSizingResult sizing,
            PreTradeRiskDecision risk, ExecutionOrderSnapshot order,
            SimulatedExecutionResult execution, PaperAccountUpdateResult accountUpdate) {
        return new PaperTradingStepResult(
                type, session, signal, sizing, risk, order, execution, accountUpdate);
    }

    private static PaperTradingException lowerFailure(String paperCode, Throwable cause) {
        String lowerCode;
        if (cause instanceof StrategySignalException exception) {
            lowerCode = exception.getErrorCode();
        } else if (cause instanceof PositionSizingException exception) {
            lowerCode = exception.getErrorCode();
        } else if (cause instanceof PreTradeRiskException exception) {
            lowerCode = exception.getErrorCode();
        } else if (cause instanceof ExecutionOrderException exception) {
            lowerCode = exception.getErrorCode();
        } else if (cause instanceof SimulatedExecutionException exception) {
            lowerCode = exception.getErrorCode();
        } else if (cause instanceof PaperAccountException exception) {
            lowerCode = exception.getErrorCode();
        } else {
            lowerCode = cause.getClass().getSimpleName();
        }
        return new PaperTradingException(
                paperCode, "lowerErrorCode=" + lowerCode + ": " + cause.getMessage(), cause);
    }

    private static PaperTradingException requestInvalid(String message) {
        return new PaperTradingException(
                PaperTradingException.PAPER_TRADING_REQUEST_INVALID, message);
    }

    private static PaperTradingException contextMismatch(String message) {
        return new PaperTradingException(
                PaperTradingException.PAPER_TRADING_CONTEXT_MISMATCH, message);
    }

    private static PaperTradingException stateInvalid(String message) {
        return new PaperTradingException(
                PaperTradingException.PAPER_TRADING_STATE_INVALID, message);
    }
}
