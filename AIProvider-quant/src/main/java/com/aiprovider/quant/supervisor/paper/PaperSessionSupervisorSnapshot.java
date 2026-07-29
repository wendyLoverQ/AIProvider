package com.aiprovider.quant.supervisor.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.audit.paper.PaperOrderAuditUpdateResult;
import com.aiprovider.quant.market.stream.model.StreamStatus;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;

import java.time.Instant;
import java.util.Objects;

public final class PaperSessionSupervisorSnapshot {
    private final PaperSessionSupervisorState state;
    private final PaperRuntimeSnapshot runtime;
    private final PaperOrderAuditLedger ledger;
    private final PaperReconciliationReport lastReconciliationReport;
    private final PaperRuntimeStepResult lastRuntimeStepResult;
    private final PaperOrderAuditUpdateResult lastAuditUpdateResult;
    private final StreamStatus streamStatus;
    private final Instant lastStreamStatusAt;
    private final String lastStreamMessage;
    private final long acceptedRuntimeStepCount;
    private final long ignoredTickerEventCount;
    private final PaperSessionSupervisorEventType lastEventType;
    private final PaperSessionSupervisorFailure failure;
    private final Instant initializedAt;
    private final Instant startedAt;
    private final Instant stoppedAt;

    public PaperSessionSupervisorSnapshot(
            PaperSessionSupervisorState state,
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            PaperReconciliationReport lastReconciliationReport,
            PaperRuntimeStepResult lastRuntimeStepResult,
            PaperOrderAuditUpdateResult lastAuditUpdateResult,
            StreamStatus streamStatus,
            Instant lastStreamStatusAt,
            String lastStreamMessage,
            long acceptedRuntimeStepCount,
            long ignoredTickerEventCount,
            PaperSessionSupervisorEventType lastEventType,
            PaperSessionSupervisorFailure failure,
            Instant initializedAt,
            Instant startedAt,
            Instant stoppedAt) {
        if (state == null || runtime == null || ledger == null || lastReconciliationReport == null
                || acceptedRuntimeStepCount < 0 || ignoredTickerEventCount < 0 || initializedAt == null
                || (state == PaperSessionSupervisorState.FAILED) != (failure != null)
                || (state == PaperSessionSupervisorState.RUNNING && startedAt == null)
                || (state == PaperSessionSupervisorState.STOPPED && stoppedAt == null)
                || (state != PaperSessionSupervisorState.RUNNING && startedAt != null
                && state == PaperSessionSupervisorState.CREATED)
                || (lastStreamStatusAt == null) != (streamStatus == null)) {
            throw new PaperSessionSupervisorException(
                    PaperSessionSupervisorExceptionCodes.REQUEST_INVALID,
                    "supervisor snapshot fields are inconsistent");
        }
        this.state = state;
        this.runtime = runtime;
        this.ledger = ledger;
        this.lastReconciliationReport = lastReconciliationReport;
        this.lastRuntimeStepResult = lastRuntimeStepResult;
        this.lastAuditUpdateResult = lastAuditUpdateResult;
        this.streamStatus = streamStatus;
        this.lastStreamStatusAt = copy(lastStreamStatusAt);
        this.lastStreamMessage = lastStreamMessage;
        this.acceptedRuntimeStepCount = acceptedRuntimeStepCount;
        this.ignoredTickerEventCount = ignoredTickerEventCount;
        this.lastEventType = lastEventType;
        this.failure = failure;
        this.initializedAt = copy(initializedAt);
        this.startedAt = copy(startedAt);
        this.stoppedAt = copy(stoppedAt);
    }

    public PaperSessionSupervisorState getState() { return state; }
    public PaperRuntimeSnapshot getRuntime() { return runtime; }
    public PaperOrderAuditLedger getLedger() { return ledger; }
    public PaperReconciliationReport getLastReconciliationReport() { return lastReconciliationReport; }
    public PaperRuntimeStepResult getLastRuntimeStepResult() { return lastRuntimeStepResult; }
    public PaperOrderAuditUpdateResult getLastAuditUpdateResult() { return lastAuditUpdateResult; }
    public StreamStatus getStreamStatus() { return streamStatus; }
    public Instant getLastStreamStatusAt() { return copy(lastStreamStatusAt); }
    public String getLastStreamMessage() { return lastStreamMessage; }
    public long getAcceptedRuntimeStepCount() { return acceptedRuntimeStepCount; }
    public long getIgnoredTickerEventCount() { return ignoredTickerEventCount; }
    public PaperSessionSupervisorEventType getLastEventType() { return lastEventType; }
    public PaperSessionSupervisorFailure getFailure() { return failure; }
    public Instant getInitializedAt() { return copy(initializedAt); }
    public Instant getStartedAt() { return copy(startedAt); }
    public Instant getStoppedAt() { return copy(stoppedAt); }

    private static Instant copy(Instant value) {
        return value == null ? null : Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperSessionSupervisorSnapshot that)) return false;
        return acceptedRuntimeStepCount == that.acceptedRuntimeStepCount
                && ignoredTickerEventCount == that.ignoredTickerEventCount
                && state == that.state && runtime.equals(that.runtime) && ledger.equals(that.ledger)
                && lastReconciliationReport.equals(that.lastReconciliationReport)
                && Objects.equals(lastRuntimeStepResult, that.lastRuntimeStepResult)
                && Objects.equals(lastAuditUpdateResult, that.lastAuditUpdateResult)
                && streamStatus == that.streamStatus
                && Objects.equals(lastStreamStatusAt, that.lastStreamStatusAt)
                && Objects.equals(lastStreamMessage, that.lastStreamMessage)
                && lastEventType == that.lastEventType && Objects.equals(failure, that.failure)
                && initializedAt.equals(that.initializedAt) && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(stoppedAt, that.stoppedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, runtime, ledger, lastReconciliationReport, lastRuntimeStepResult,
                lastAuditUpdateResult, streamStatus, lastStreamStatusAt, lastStreamMessage,
                acceptedRuntimeStepCount, ignoredTickerEventCount, lastEventType, failure,
                initializedAt, startedAt, stoppedAt);
    }

    static final class PaperSessionSupervisorExceptionCodes {
        static final String REQUEST_INVALID = "PAPER_SUPERVISOR_REQUEST_INVALID";
        private PaperSessionSupervisorExceptionCodes() { }
    }
}
