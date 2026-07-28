package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.account.paper.DefaultPaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountEngine;
import com.aiprovider.quant.account.paper.PaperAccountException;
import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.engine.paper.DefaultPaperTradingEngine;
import com.aiprovider.quant.engine.paper.PaperTradingEngine;
import com.aiprovider.quant.engine.paper.PaperTradingException;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingStepResult;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.runtime.DefaultRuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketState;
import com.aiprovider.quant.market.runtime.RuntimeMarketStateEngine;
import com.aiprovider.quant.market.runtime.RuntimeMarketStateException;
import com.aiprovider.quant.market.runtime.RuntimeMarketUpdateResult;
import com.aiprovider.quant.market.runtime.RuntimeMarketUpdateType;
import com.aiprovider.quant.market.runtime.RuntimeTopOfBook;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Deterministic event-driven orchestration over the existing market, trading and account engines. */
public final class DefaultPaperRuntimeEngine implements PaperRuntimeEngine {
    private final RuntimeMarketStateEngine marketEngine;
    private final PaperTradingEngine tradingEngine;
    private final PaperAccountEngine accountEngine;

    public DefaultPaperRuntimeEngine() {
        this(new DefaultRuntimeMarketStateEngine(), new DefaultPaperTradingEngine(),
                new DefaultPaperAccountEngine());
    }

    public DefaultPaperRuntimeEngine(RuntimeMarketStateEngine marketEngine,
                                     PaperTradingEngine tradingEngine,
                                     PaperAccountEngine accountEngine) {
        if (marketEngine == null || tradingEngine == null || accountEngine == null) {
            throw requestInvalid("marketEngine, tradingEngine and accountEngine are required");
        }
        this.marketEngine = marketEngine;
        this.tradingEngine = tradingEngine;
        this.accountEngine = accountEngine;
    }

    @Override
    public PaperRuntimeSnapshot initialize(PaperRuntimeConfig config,
                                           List<HistoricalCandle> seedCandles,
                                           PaperAccountSnapshot account) {
        if (config == null || seedCandles == null || account == null) {
            throw requestInvalid("config, seedCandles and account are required");
        }
        RuntimeMarketState marketState;
        try {
            marketState = marketEngine.initialize(
                    config.getMarketKey(), config.getMaxClosedCandles(), seedCandles);
        } catch (RuntimeMarketStateException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getErrorCode(), exception);
        } catch (RuntimeException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getClass().getSimpleName(), exception);
        }
        PaperTradingSessionSnapshot session;
        try {
            session = tradingEngine.createSession(config.getTradingConfig(), account);
        } catch (PaperTradingException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                    exception.getErrorCode(), exception);
        } catch (RuntimeException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                    exception.getClass().getSimpleName(), exception);
        }
        return new PaperRuntimeSnapshot(config, marketState, session, null, null);
    }

    @Override
    public PaperRuntimeStepResult onKline(PaperRuntimeSnapshot runtime, StreamKlineEvent event) {
        requireRuntimeAndTime(runtime, event == null ? null : event.getEventTime(), event);
        RuntimeMarketUpdateResult marketUpdate = updateKline(runtime, event);
        Rollover rollover = rollTradingDay(runtime.getTradingSession(), event.getEventTime());
        PaperTradingSessionSnapshot session = rollover.session;
        PaperTradingStepResult tradingResult = null;
        PaperRuntimeStepType stepType;
        RuntimeMarketUpdateType updateType = marketUpdate.getUpdateType();
        if (updateType == RuntimeMarketUpdateType.OPEN_KLINE_IGNORED) {
            stepType = PaperRuntimeStepType.OPEN_KLINE_IGNORED;
        } else if (updateType == RuntimeMarketUpdateType.DUPLICATE_CLOSED_CANDLE_IGNORED) {
            stepType = PaperRuntimeStepType.DUPLICATE_CLOSED_CANDLE_IGNORED;
        } else if (updateType == RuntimeMarketUpdateType.CLOSED_CANDLE_APPENDED) {
            try {
                tradingResult = tradingEngine.evaluateClosedCandles(
                        session, marketUpdate.getState().toHistoricalCandles(), event.getEventTime());
            } catch (PaperTradingException exception) {
                throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                        exception.getErrorCode(), exception);
            } catch (RuntimeException exception) {
                throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                        exception.getClass().getSimpleName(), exception);
            }
            session = tradingResult.getSession();
            stepType = PaperRuntimeStepType.CLOSED_CANDLE_PROCESSED;
        } else {
            throw stateInvalid("unexpected kline update type: " + updateType);
        }
        return result(runtime, event.getEventTime(), marketUpdate, session, tradingResult,
                stepType, rollover);
    }

    @Override
    public PaperRuntimeStepResult onBookTicker(PaperRuntimeSnapshot runtime,
                                               StreamBookTickerEvent event) {
        requireRuntimeAndTime(runtime, event == null ? null : event.getEventTime(), event);
        RuntimeMarketUpdateResult marketUpdate = updateBook(runtime, event);
        Rollover rollover = rollTradingDay(runtime.getTradingSession(), event.getEventTime());
        PaperTradingSessionSnapshot session = rollover.session;
        PaperTradingStepResult tradingResult = null;
        PaperRuntimeStepType stepType;
        RuntimeMarketUpdateType updateType = marketUpdate.getUpdateType();
        if (updateType == RuntimeMarketUpdateType.DUPLICATE_TOP_OF_BOOK_IGNORED) {
            stepType = PaperRuntimeStepType.DUPLICATE_TOP_OF_BOOK_IGNORED;
        } else if (updateType == RuntimeMarketUpdateType.TOP_OF_BOOK_UPDATED
                && session.getPendingOrderSnapshot() == null) {
            stepType = PaperRuntimeStepType.TOP_OF_BOOK_UPDATED;
        } else if (updateType == RuntimeMarketUpdateType.TOP_OF_BOOK_UPDATED) {
            RuntimeTopOfBook book = marketUpdate.getState().getLatestTopOfBook();
            SimulatedTopOfBook simulated = new SimulatedTopOfBook(
                    book.getProvider(), book.getMarketType(), book.getSymbol(), book.getEventTime(),
                    book.getBidPrice(), book.getBidQuantity(), book.getAskPrice(), book.getAskQuantity());
            try {
                tradingResult = tradingEngine.executePendingOrder(session, simulated);
            } catch (PaperTradingException exception) {
                throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                        exception.getErrorCode(), exception);
            } catch (RuntimeException exception) {
                throw lower(PaperRuntimeException.PAPER_RUNTIME_TRADING_FAILED,
                        exception.getClass().getSimpleName(), exception);
            }
            session = tradingResult.getSession();
            stepType = PaperRuntimeStepType.PENDING_ORDER_EXECUTED;
        } else {
            throw stateInvalid("unexpected book ticker update type: " + updateType);
        }
        return result(runtime, event.getEventTime(), marketUpdate, session, tradingResult,
                stepType, rollover);
    }

    private RuntimeMarketUpdateResult updateKline(PaperRuntimeSnapshot runtime,
                                                   StreamKlineEvent event) {
        try {
            return marketEngine.onKline(runtime.getMarketState(), event);
        } catch (RuntimeMarketStateException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getErrorCode(), exception);
        } catch (RuntimeException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getClass().getSimpleName(), exception);
        }
    }

    private RuntimeMarketUpdateResult updateBook(PaperRuntimeSnapshot runtime,
                                                  StreamBookTickerEvent event) {
        try {
            return marketEngine.onBookTicker(runtime.getMarketState(), event);
        } catch (RuntimeMarketStateException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getErrorCode(), exception);
        } catch (RuntimeException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_MARKET_FAILED,
                    exception.getClass().getSimpleName(), exception);
        }
    }

    private Rollover rollTradingDay(PaperTradingSessionSnapshot session, Instant eventTime) {
        PaperAccountSnapshot account = session.getPaperAccountSnapshot();
        LocalDate previousDate = account.getTradingDayState().getUtcDate();
        LocalDate eventDate = eventTime.atZone(ZoneOffset.UTC).toLocalDate();
        if (eventDate.isBefore(previousDate)) {
            throw new PaperRuntimeException(
                    PaperRuntimeException.PAPER_RUNTIME_EVENT_DATE_INVALID,
                    "eventUtcDate=" + eventDate + " is earlier than accountUtcDate=" + previousDate);
        }
        if (eventDate.equals(previousDate)) {
            return new Rollover(session, false, previousDate, previousDate);
        }
        PaperAccountSnapshot rolledAccount;
        try {
            rolledAccount = accountEngine.rollUtcTradingDay(account, eventDate, eventTime);
        } catch (PaperAccountException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_ACCOUNT_FAILED,
                    exception.getErrorCode(), exception);
        } catch (RuntimeException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_ACCOUNT_FAILED,
                    exception.getClass().getSimpleName(), exception);
        }
        PaperTradingSessionSnapshot rolledSession;
        try {
            rolledSession = new PaperTradingSessionSnapshot(
                    session.getConfig(), rolledAccount, session.getPendingOrderSnapshot(),
                    session.getLastOrderSnapshot(), session.getLastSignalDecision(),
                    session.getLastSizingResult(), session.getLastRiskDecision(),
                    session.getLastEvaluatedCandle(), rolledAccount.getLastUpdatedAt());
        } catch (PaperTradingException exception) {
            throw lower(PaperRuntimeException.PAPER_RUNTIME_STATE_INVALID,
                    exception.getErrorCode(), exception);
        }
        return new Rollover(rolledSession, true, previousDate, eventDate);
    }

    private PaperRuntimeStepResult result(
            PaperRuntimeSnapshot previous, Instant eventTime,
            RuntimeMarketUpdateResult marketUpdate, PaperTradingSessionSnapshot session,
            PaperTradingStepResult tradingResult, PaperRuntimeStepType stepType,
            Rollover rollover) {
        PaperRuntimeSnapshot updated = new PaperRuntimeSnapshot(
                previous.getConfig(), marketUpdate.getState(), session, eventTime, stepType);
        return new PaperRuntimeStepResult(stepType, updated, marketUpdate, tradingResult,
                rollover.rolled, rollover.previousDate, rollover.currentDate);
    }

    private static void requireRuntimeAndTime(PaperRuntimeSnapshot runtime, Instant eventTime,
                                              Object event) {
        if (runtime == null || event == null) {
            throw requestInvalid("runtime and event are required");
        }
        if (eventTime == null) {
            throw new PaperRuntimeException(
                    PaperRuntimeException.PAPER_RUNTIME_EVENT_TIME_INVALID,
                    "eventTime is required");
        }
    }

    private static PaperRuntimeException requestInvalid(String message) {
        return new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_REQUEST_INVALID, message);
    }

    private static PaperRuntimeException stateInvalid(String message) {
        return new PaperRuntimeException(PaperRuntimeException.PAPER_RUNTIME_STATE_INVALID, message);
    }

    private static PaperRuntimeException lower(String code, String lowerCode, Throwable cause) {
        return new PaperRuntimeException(code,
                "lowerErrorCode=" + lowerCode + ": " + cause.getMessage(), cause);
    }

    private static final class Rollover {
        private final PaperTradingSessionSnapshot session;
        private final boolean rolled;
        private final LocalDate previousDate;
        private final LocalDate currentDate;

        private Rollover(PaperTradingSessionSnapshot session, boolean rolled,
                         LocalDate previousDate, LocalDate currentDate) {
            this.session = session;
            this.rolled = rolled;
            this.previousDate = previousDate;
            this.currentDate = currentDate;
        }
    }
}
