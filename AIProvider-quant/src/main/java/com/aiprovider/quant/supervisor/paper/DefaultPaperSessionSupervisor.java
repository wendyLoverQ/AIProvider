package com.aiprovider.quant.supervisor.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.audit.paper.PaperOrderAuditUpdateResult;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamStatus;
import com.aiprovider.quant.market.stream.model.StreamStatusEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;
import com.aiprovider.quant.market.stream.port.MarketStreamClient;
import com.aiprovider.quant.runtime.paper.PaperRuntimeEngine;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes one paper runtime, its audit ledger, and its stream subscription. */
public final class DefaultPaperSessionSupervisor implements PaperSessionSupervisor {
    public static final String REQUEST_INVALID = "PAPER_SUPERVISOR_REQUEST_INVALID";
    public static final String CONTEXT_MISMATCH = "PAPER_SUPERVISOR_CONTEXT_MISMATCH";
    public static final String STATE_INVALID = "PAPER_SUPERVISOR_STATE_INVALID";
    public static final String INITIAL_STATE_INCONSISTENT = "PAPER_SUPERVISOR_INITIAL_STATE_INCONSISTENT";
    public static final String STREAM_SUBSCRIBE_FAILED = "PAPER_SUPERVISOR_STREAM_SUBSCRIBE_FAILED";
    public static final String STREAM_UNSUBSCRIBE_FAILED = "PAPER_SUPERVISOR_STREAM_UNSUBSCRIBE_FAILED";
    public static final String RUNTIME_FAILED = "PAPER_SUPERVISOR_RUNTIME_FAILED";
    public static final String AUDIT_FAILED = "PAPER_SUPERVISOR_AUDIT_FAILED";
    public static final String RECONCILIATION_INCONSISTENT =
            "PAPER_SUPERVISOR_RECONCILIATION_INCONSISTENT";
    public static final String STREAM_FAILED = "PAPER_SUPERVISOR_STREAM_FAILED";

    private final ReentrantLock lock = new ReentrantLock();
    private final MarketStreamClient marketStreamClient;
    private final PaperRuntimeEngine runtimeEngine;
    private final PaperOrderAuditEngine auditEngine;
    private final PaperRuntimeSnapshot initialRuntime;
    private final Instant initializedAt;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final KlineInterval interval;

    private PaperSessionSupervisorState state = PaperSessionSupervisorState.CREATED;
    private PaperRuntimeSnapshot runtime;
    private PaperOrderAuditLedger ledger;
    private PaperReconciliationReport reconciliationReport;
    private PaperRuntimeStepResult lastRuntimeStepResult;
    private PaperOrderAuditUpdateResult lastAuditUpdateResult;
    private StreamStatus streamStatus;
    private Instant lastStreamStatusAt;
    private String lastStreamMessage;
    private long acceptedRuntimeStepCount;
    private long ignoredTickerEventCount;
    private PaperSessionSupervisorEventType lastEventType;
    private PaperSessionSupervisorFailure failure;
    private Instant startedAt;
    private Instant stoppedAt;
    private boolean unsubscribeAttempted;
    private boolean subscribed;

    public DefaultPaperSessionSupervisor(
            MarketStreamClient marketStreamClient,
            PaperRuntimeEngine runtimeEngine,
            PaperOrderAuditEngine auditEngine,
            PaperRuntimeSnapshot initialRuntime,
            PaperOrderAuditLedger initialLedger,
            Instant initializedAt) {
        this.marketStreamClient = require(marketStreamClient, "marketStreamClient");
        this.runtimeEngine = require(runtimeEngine, "runtimeEngine");
        this.auditEngine = require(auditEngine, "auditEngine");
        this.initialRuntime = require(initialRuntime, "initialRuntime");
        this.ledger = require(initialLedger, "initialLedger");
        this.initializedAt = require(initializedAt, "initializedAt");

        this.runtime = initialRuntime;
        this.provider = initialRuntime.getConfig().getMarketKey().getProvider();
        this.marketType = initialRuntime.getConfig().getMarketKey().getMarketType();
        this.symbol = initialRuntime.getConfig().getMarketKey().getSymbol();
        this.interval = initialRuntime.getConfig().getMarketKey().getInterval();
        validateInitialContext();
        this.reconciliationReport = reconcileInitialState();
    }

    @Override
    public PaperSessionSupervisorSnapshot start(Instant requestedStartedAt) {
        lock.lock();
        try {
            requireTime(requestedStartedAt, "startedAt");
            if (state != PaperSessionSupervisorState.CREATED) {
                throw error(STATE_INVALID, "start is only valid from CREATED; state=" + state);
            }
            if (requestedStartedAt.isBefore(initializedAt)) {
                throw error(REQUEST_INVALID, "startedAt must not precede initializedAt");
            }
            state = PaperSessionSupervisorState.RUNNING;
            startedAt = copy(requestedStartedAt);
            lastEventType = PaperSessionSupervisorEventType.STARTED;
            subscribed = true;
            try {
                marketStreamClient.subscribe(provider, symbol, interval, this);
                if (state == PaperSessionSupervisorState.FAILED) {
                    throw error(failure.getErrorCode(), failure.getMessage(), failure.getCause());
                }
                return snapshot();
            } catch (RuntimeException exception) {
                if (state == PaperSessionSupervisorState.FAILED
                        && failure != null
                        && !STREAM_SUBSCRIBE_FAILED.equals(failure.getErrorCode())) {
                    throw exception;
                }
                failLocked(STREAM_SUBSCRIBE_FAILED, "Market stream subscription failed", exception);
                throw error(STREAM_SUBSCRIBE_FAILED, "Market stream subscription failed", exception);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PaperSessionSupervisorSnapshot stop(Instant requestedStoppedAt) {
        lock.lock();
        try {
            requireTime(requestedStoppedAt, "stoppedAt");
            if (state == PaperSessionSupervisorState.STOPPED) {
                return snapshot();
            }
            if (state == PaperSessionSupervisorState.CREATED) {
                state = PaperSessionSupervisorState.STOPPED;
                stoppedAt = copy(requestedStoppedAt);
                lastEventType = PaperSessionSupervisorEventType.STOPPED;
                return snapshot();
            }
            if (state == PaperSessionSupervisorState.FAILED) {
                state = PaperSessionSupervisorState.STOPPED;
                stoppedAt = copy(requestedStoppedAt);
                failure = null;
                lastEventType = PaperSessionSupervisorEventType.STOPPED;
                return snapshot();
            }
            try {
                unsubscribeLocked();
                state = PaperSessionSupervisorState.STOPPED;
                stoppedAt = copy(requestedStoppedAt);
                lastEventType = PaperSessionSupervisorEventType.STOPPED;
                return snapshot();
            } catch (RuntimeException exception) {
                failLocked(STREAM_UNSUBSCRIBE_FAILED, "Market stream unsubscription failed", exception);
                throw error(STREAM_UNSUBSCRIBE_FAILED, "Market stream unsubscription failed", exception);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PaperSessionSupervisorSnapshot getSnapshot() {
        lock.lock();
        try {
            return snapshot();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onKline(StreamKlineEvent event) {
        processEvent(event, event == null ? null : event.getEventTime(),
                PaperSessionSupervisorEventType.KLINE_PROCESSED,
                () -> runtimeEngine.onKline(runtime, event));
    }

    @Override
    public void onBookTicker(StreamBookTickerEvent event) {
        processEvent(event, event == null ? null : event.getEventTime(),
                PaperSessionSupervisorEventType.BOOK_TICKER_PROCESSED,
                () -> runtimeEngine.onBookTicker(runtime, event));
    }

    @Override
    public void onMarkPrice(StreamMarkPriceEvent event) {
        processEvent(event, event == null ? null : event.getEventTime(),
                PaperSessionSupervisorEventType.MARK_PRICE_PROCESSED,
                () -> runtimeEngine.onMarkPrice(runtime, event));
    }

    @Override
    public void onTicker(StreamTickerEvent event) {
        lock.lock();
        try {
            if (state != PaperSessionSupervisorState.RUNNING) return;
            validateTicker(event);
            ignoredTickerEventCount = Math.addExact(ignoredTickerEventCount, 1L);
            lastEventType = PaperSessionSupervisorEventType.TICKER_IGNORED;
        } catch (RuntimeException exception) {
            handleCallbackFailure(exception, REQUEST_INVALID);
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onStatus(StreamStatusEvent event) {
        lock.lock();
        try {
            if (state != PaperSessionSupervisorState.RUNNING) return;
            validateStatus(event);
            streamStatus = event.getStatus();
            lastStreamStatusAt = copy(event.getTimestamp());
            lastStreamMessage = event.getMessage();
            lastEventType = PaperSessionSupervisorEventType.STREAM_STATUS_UPDATED;
            if (event.getStatus() == StreamStatus.FAILED) {
                PaperSessionSupervisorException exception = error(
                        STREAM_FAILED, "Upstream market stream reported FAILED");
                failLocked(STREAM_FAILED, exception.getMessage(), exception);
            }
        } catch (RuntimeException exception) {
            if (state == PaperSessionSupervisorState.RUNNING) {
                handleCallbackFailure(exception, exception instanceof PaperSessionSupervisorException
                        ? ((PaperSessionSupervisorException) exception).getErrorCode() : REQUEST_INVALID);
            }
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    private void processEvent(Object event, Instant eventTime,
                              PaperSessionSupervisorEventType eventType,
                              RuntimeStepCall call) {
        lock.lock();
        try {
            if (state != PaperSessionSupervisorState.RUNNING) return;
            validateMarketEvent(event, eventTime);
            PaperRuntimeStepResult result;
            try {
                result = call.call();
            } catch (RuntimeException exception) {
                throw error(RUNTIME_FAILED, "Paper runtime step failed", exception);
            }
            if (result == null || result.getRuntime() == null) {
                throw error(RUNTIME_FAILED, "PaperRuntimeEngine returned null runtime result");
            }
            Instant auditAt = max(eventTime, result.getRuntime().getTradingSession().getLastUpdatedAt(),
                    ledger.getLastUpdatedAt());
            PaperOrderAuditUpdateResult auditResult;
            try {
                auditResult = auditEngine.record(ledger, result, auditAt);
            } catch (RuntimeException exception) {
                throw error(AUDIT_FAILED, "Paper order audit failed", exception);
            }
            Instant reconciledAt = max(auditAt, auditResult.getLedger().getLastUpdatedAt());
            PaperReconciliationReport report;
            try {
                report = auditEngine.reconcile(auditResult.getLedger(), result.getRuntime(), reconciledAt);
            } catch (RuntimeException exception) {
                throw error(AUDIT_FAILED, "Paper order reconciliation call failed", exception);
            }
            if (report == null) {
                throw error(AUDIT_FAILED, "PaperOrderAuditEngine returned null reconciliation report");
            }
            if (report.getStatus() != PaperReconciliationStatus.CONSISTENT) {
                throw error(RECONCILIATION_INCONSISTENT,
                        "Paper reconciliation is inconsistent; violations=" + violationCodes(report), null);
            }
            runtime = result.getRuntime();
            ledger = auditResult.getLedger();
            reconciliationReport = report;
            lastRuntimeStepResult = result;
            lastAuditUpdateResult = auditResult;
            acceptedRuntimeStepCount = Math.addExact(acceptedRuntimeStepCount, 1L);
            lastEventType = eventType;
        } catch (RuntimeException exception) {
            String code = exception instanceof PaperSessionSupervisorException
                    ? ((PaperSessionSupervisorException) exception).getErrorCode() : RUNTIME_FAILED;
            handleCallbackFailure(exception, code);
            if (exception instanceof PaperSessionSupervisorException) throw exception;
            throw error(code, "Paper runtime callback failed", exception);
        } finally {
            lock.unlock();
        }
    }

    private void validateInitialContext() {
        if (!ledger.getSessionId().equals(runtime.getTradingSession().getConfig().getSessionId())
                || ledger.getProvider() != provider || ledger.getMarketType() != marketType
                || !ledger.getSymbol().equals(symbol)) {
            throw error(CONTEXT_MISMATCH, "initial Runtime and Ledger contexts do not match");
        }
        Instant sessionTime = runtime.getTradingSession().getLastUpdatedAt();
        if (initializedAt.isBefore(sessionTime) || initializedAt.isBefore(ledger.getLastUpdatedAt())) {
            throw error(REQUEST_INVALID, "initializedAt must not precede Runtime Session or Ledger lastUpdatedAt");
        }
        Instant kline = runtime.getRuntimeMarketState().getLastKlineEventTime();
        Instant book = runtime.getRuntimeMarketState().getLastBookTickerEventTime();
        Instant mark = runtime.getRuntimeMarketState().getLastMarkPriceEventTime();
        if ((kline != null && initializedAt.isBefore(kline))
                || (book != null && initializedAt.isBefore(book))
                || (mark != null && initializedAt.isBefore(mark))) {
            throw error(REQUEST_INVALID, "initializedAt must not precede a market stream watermark");
        }
    }

    private PaperReconciliationReport reconcileInitialState() {
        try {
            PaperReconciliationReport report = auditEngine.reconcile(ledger, runtime, initializedAt);
            if (report == null) {
                throw new IllegalStateException("PaperOrderAuditEngine returned null reconciliation report");
            }
            if (report.getStatus() != PaperReconciliationStatus.CONSISTENT) {
                throw error(INITIAL_STATE_INCONSISTENT,
                        "initial state is inconsistent; violations=" + violationCodes(report));
            }
            return report;
        } catch (PaperSessionSupervisorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw error(INITIAL_STATE_INCONSISTENT, "initial state reconciliation failed", exception);
        }
    }

    private void validateMarketEvent(Object event, Instant eventTime) {
        if (event == null || eventTime == null) {
            throw error(REQUEST_INVALID, "market event and eventTime are required");
        }
        if (event instanceof StreamKlineEvent) {
            StreamKlineEvent value = (StreamKlineEvent) event;
            if (value.getProvider() != provider || value.getMarketType() != marketType
                    || !symbol.equals(value.getSymbol()) || value.getInterval() != interval) {
                throw error(CONTEXT_MISMATCH, "Kline event context does not match supervisor");
            }
        } else if (event instanceof StreamBookTickerEvent) {
            StreamBookTickerEvent value = (StreamBookTickerEvent) event;
            validateContext(value.getProvider(), value.getMarketType(), value.getSymbol(), "BookTicker");
        } else if (event instanceof StreamMarkPriceEvent) {
            StreamMarkPriceEvent value = (StreamMarkPriceEvent) event;
            validateContext(value.getProvider(), value.getMarketType(), value.getSymbol(), "MarkPrice");
        }
    }

    private void validateTicker(StreamTickerEvent event) {
        if (event == null || event.getEventTime() == null) {
            throw error(REQUEST_INVALID, "Ticker event and eventTime are required");
        }
        validateContext(event.getProvider(), event.getMarketType(), event.getSymbol(), "Ticker");
    }

    private void validateStatus(StreamStatusEvent event) {
        if (event == null || event.getStatus() == null || event.getTimestamp() == null) {
            throw error(REQUEST_INVALID, "Stream status, status timestamp and status are required");
        }
        if (event.getProvider() != provider || !symbol.equals(event.getSymbol())
                || event.getInterval() != interval) {
            throw error(CONTEXT_MISMATCH, "Stream status context does not match supervisor");
        }
    }

    private void validateContext(MarketProviderId eventProvider, MarketType eventMarketType,
                                 String eventSymbol, String label) {
        if (eventProvider != provider || eventMarketType != marketType || !symbol.equals(eventSymbol)) {
            throw error(CONTEXT_MISMATCH, label + " event context does not match supervisor");
        }
    }

    private void handleCallbackFailure(RuntimeException exception, String code) {
        if (state == PaperSessionSupervisorState.RUNNING) {
            String resolved = code == null || code.isBlank() ? RUNTIME_FAILED : code;
            failLocked(resolved, exception.getMessage() == null ? resolved : exception.getMessage(), exception);
        }
    }

    private void failLocked(String code, String message, Throwable cause) {
        state = PaperSessionSupervisorState.FAILED;
        failure = new PaperSessionSupervisorFailure(code, message == null ? code : message, cause);
        lastEventType = PaperSessionSupervisorEventType.FAILED;
        try {
            unsubscribeLocked();
        } catch (RuntimeException unsubscribeFailure) {
            if (cause != null) cause.addSuppressed(unsubscribeFailure);
        }
    }

    private void unsubscribeLocked() {
        if (unsubscribeAttempted || !subscribed) return;
        unsubscribeAttempted = true;
        marketStreamClient.unsubscribe(provider, symbol, interval, this);
    }

    private PaperSessionSupervisorSnapshot snapshot() {
        return new PaperSessionSupervisorSnapshot(state, runtime, ledger, reconciliationReport,
                lastRuntimeStepResult, lastAuditUpdateResult, streamStatus, lastStreamStatusAt,
                lastStreamMessage, acceptedRuntimeStepCount, ignoredTickerEventCount, lastEventType,
                failure, initializedAt, startedAt, stoppedAt);
    }

    private static String violationCodes(PaperReconciliationReport report) {
        List<String> codes = new ArrayList<>();
        report.getViolations().forEach(violation -> {
            String code = violation.getCode().name();
            if (!codes.contains(code)) codes.add(code);
        });
        return codes.toString();
    }

    private static Instant max(Instant... values) {
        Instant result = null;
        for (Instant value : values) {
            if (value != null && (result == null || value.isAfter(result))) result = value;
        }
        if (result == null) throw error(REQUEST_INVALID, "at least one audit timestamp is required");
        return result;
    }

    private static Instant copy(Instant value) {
        return value == null ? null : Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw error(REQUEST_INVALID, name + " is required");
        return value;
    }

    private static void requireTime(Instant value, String name) {
        if (value == null) throw error(REQUEST_INVALID, name + " is required");
    }

    private static PaperSessionSupervisorException error(String code, String message) {
        return new PaperSessionSupervisorException(code, message);
    }

    private static PaperSessionSupervisorException error(String code, String message, Throwable cause) {
        return new PaperSessionSupervisorException(code, message, cause);
    }

    @FunctionalInterface
    private interface RuntimeStepCall {
        PaperRuntimeStepResult call();
    }
}
