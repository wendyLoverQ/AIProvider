package com.aiprovider.quant.audit.paper;

import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.reconciliation.paper.PaperReconciliationReport;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;
import com.aiprovider.quant.runtime.paper.PaperRuntimeStepResult;

import java.time.Instant;
import java.util.List;

public interface PaperOrderAuditEngine {
    PaperOrderAuditLedger initialize(
            PaperRuntimeSnapshot runtime,
            List<ExecutionOrderSnapshot> seedOrderHistory,
            Instant initializedAt);

    PaperOrderAuditUpdateResult record(
            PaperOrderAuditLedger ledger,
            PaperRuntimeStepResult runtimeStepResult,
            Instant recordedAt);

    PaperReconciliationReport reconcile(
            PaperOrderAuditLedger ledger,
            PaperRuntimeSnapshot runtime,
            Instant reconciledAt);
}
