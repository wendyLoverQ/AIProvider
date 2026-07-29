package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;

import java.time.Instant;

public final class PaperCheckpointRestoreResult {
    private final PaperRuntimeSnapshot runtime;
    private final PaperOrderAuditLedger ledger;
    private final PaperReconciliationReport reconciliationReport;
    private final long checkpointVersion;
    private final Instant checkpointCreatedAt;
    private final Instant restoredAt;

    public PaperCheckpointRestoreResult(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            PaperReconciliationReport reconciliationReport,
            long checkpointVersion,
            Instant checkpointCreatedAt,
            Instant restoredAt) {
        if (runtime == null || ledger == null || reconciliationReport == null
                || checkpointVersion < 0 || checkpointCreatedAt == null || restoredAt == null) {
            throw new PaperCheckpointException(
                    PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "restore result fields are required and version must be non-negative");
        }
        if (restoredAt.isBefore(checkpointCreatedAt)) {
            throw new PaperCheckpointException(
                    PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID,
                    "restoredAt must not precede checkpointCreatedAt");
        }
        this.runtime = runtime;
        this.ledger = ledger;
        this.reconciliationReport = reconciliationReport;
        this.checkpointVersion = checkpointVersion;
        this.checkpointCreatedAt = copy(checkpointCreatedAt);
        this.restoredAt = copy(restoredAt);
    }

    public PaperRuntimeSnapshot getRuntime() { return runtime; }
    public PaperOrderAuditLedger getLedger() { return ledger; }
    public PaperReconciliationReport getReconciliationReport() { return reconciliationReport; }
    public long getCheckpointVersion() { return checkpointVersion; }
    public Instant getCheckpointCreatedAt() { return copy(checkpointCreatedAt); }
    public Instant getRestoredAt() { return copy(restoredAt); }

    private static Instant copy(Instant value) {
        return Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }
}
