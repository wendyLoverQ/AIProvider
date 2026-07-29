package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditEngine;
import com.aiprovider.quant.audit.paper.PaperOrderAuditException;
import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.audit.paper.DefaultPaperOrderAuditEngine;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationStatus;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DefaultPaperCheckpointEngine implements PaperCheckpointEngine {
    private final PaperOrderAuditEngine auditEngine;

    public DefaultPaperCheckpointEngine() {
        this(new DefaultPaperOrderAuditEngine());
    }

    public DefaultPaperCheckpointEngine(PaperOrderAuditEngine auditEngine) {
        if (auditEngine == null) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "auditEngine is required");
        }
        this.auditEngine = auditEngine;
    }

    @Override
    public PaperRuntimeCheckpoint create(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            long version,
            Instant createdAt) {
        validateCreateRequest(runtime, ledger, version, createdAt);
        validateContext(runtime, ledger);
        validateCreationTime(runtime, ledger, createdAt);
        PaperReconciliationReport report = reconcileForCreate(runtime, ledger, createdAt);
        requireConsistent(report, PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT,
                "checkpoint creation reconciliation is inconsistent");
        return new PaperRuntimeCheckpoint(
                ledger.getSessionId(), ledger.getProvider(), ledger.getMarketType(),
                ledger.getSymbol(), runtime.getConfig().getMarketKey().getInterval(), version,
                runtime, ledger, report, createdAt);
    }

    @Override
    public PaperCheckpointRestoreResult restore(
            PaperRuntimeCheckpoint checkpoint,
            Instant restoredAt) {
        if (checkpoint == null || restoredAt == null) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "checkpoint and restoredAt are required");
        }
        validateCheckpointContext(checkpoint);
        if (restoredAt.isBefore(checkpoint.getCreatedAt())) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID,
                    "restoredAt must not precede checkpoint createdAt");
        }
        PaperReconciliationReport report;
        try {
            report = auditEngine.reconcile(
                    checkpoint.getLedger(), checkpoint.getRuntime(), restoredAt);
        } catch (PaperCheckpointException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_RESTORE_INCONSISTENT,
                    "checkpoint restore reconciliation failed", exception);
        }
        requireConsistent(report, PaperCheckpointException.PAPER_CHECKPOINT_RESTORE_INCONSISTENT,
                "checkpoint restore reconciliation is inconsistent");
        if (!restoredAt.equals(report.getReconciledAt())) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_RESTORE_INCONSISTENT,
                    "restored reconciliation report time must equal restoredAt");
        }
        return new PaperCheckpointRestoreResult(
                checkpoint.getRuntime(), checkpoint.getLedger(), report,
                checkpoint.getVersion(), checkpoint.getCreatedAt(), restoredAt);
    }

    private void validateCreateRequest(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            long version,
            Instant createdAt) {
        if (runtime == null || ledger == null || createdAt == null || version < 0) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "runtime, ledger and createdAt are required; version must be non-negative");
        }
    }

    private void validateContext(PaperRuntimeSnapshot runtime, PaperOrderAuditLedger ledger) {
        String runtimeSessionId = runtime.getTradingSession().getConfig().getSessionId();
        if (!runtimeSessionId.equals(ledger.getSessionId())
                || runtime.getConfig().getMarketKey().getProvider() != ledger.getProvider()
                || runtime.getConfig().getMarketKey().getMarketType() != ledger.getMarketType()
                || !runtime.getConfig().getMarketKey().getSymbol().equals(ledger.getSymbol())) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_CONTEXT_MISMATCH,
                    "Runtime and Ledger context do not match");
        }
    }

    private void validateCheckpointContext(PaperRuntimeCheckpoint checkpoint) {
        if (checkpoint.getVersion() < 0 || checkpoint.getRuntime() == null
                || checkpoint.getLedger() == null || checkpoint.getReconciliationReport() == null
                || checkpoint.getReconciliationReport().getStatus()
                != PaperReconciliationStatus.CONSISTENT) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "checkpoint fields are invalid");
        }
        if (!checkpoint.getSessionId().equals(checkpoint.getLedger().getSessionId())
                || !checkpoint.getSessionId().equals(
                checkpoint.getRuntime().getTradingSession().getConfig().getSessionId())) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_CONTEXT_MISMATCH,
                    "checkpoint session context does not match");
        }
    }

    private void validateCreationTime(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            Instant createdAt) {
        Instant latestStateTime = PaperRuntimeCheckpoint.latestStateTime(runtime, ledger);
        if (createdAt.isBefore(latestStateTime)) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_TIME_INVALID,
                    "createdAt must not precede latest state time=" + latestStateTime);
        }
    }

    private PaperReconciliationReport reconcileForCreate(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            Instant createdAt) {
        try {
            PaperReconciliationReport report = auditEngine.reconcile(ledger, runtime, createdAt);
            if (report == null) {
                throw new IllegalStateException("auditEngine returned null reconciliation report");
            }
            return report;
        } catch (PaperOrderAuditException exception) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT,
                    "checkpoint creation audit failed: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_STATE_INCONSISTENT,
                    "checkpoint creation reconciliation failed", exception);
        }
    }

    private void requireConsistent(
            PaperReconciliationReport report, String errorCode, String prefix) {
        if (report == null) {
            throw error(errorCode, prefix + ": report is null");
        }
        if (report.getStatus() != PaperReconciliationStatus.CONSISTENT) {
            List<String> codes = new ArrayList<>();
            report.getViolations().forEach(violation -> {
                String code = violation.getCode().name();
                if (!codes.contains(code)) {
                    codes.add(code);
                }
            });
            throw error(errorCode, prefix + ": violations=" + codes);
        }
    }

    private static PaperCheckpointException error(String code, String message) {
        return new PaperCheckpointException(code, message);
    }

    private static PaperCheckpointException error(
            String code, String message, Throwable cause) {
        return new PaperCheckpointException(code, message, cause);
    }
}
