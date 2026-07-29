package com.aiprovider.quant.checkpoint.paper;

import com.aiprovider.quant.audit.paper.PaperOrderAuditLedger;
import com.aiprovider.quant.runtime.paper.PaperRuntimeSnapshot;

import java.time.Instant;

public interface PaperCheckpointEngine {
    PaperRuntimeCheckpoint create(
            PaperRuntimeSnapshot runtime,
            PaperOrderAuditLedger ledger,
            long version,
            Instant createdAt);

    PaperCheckpointRestoreResult restore(
            PaperRuntimeCheckpoint checkpoint,
            Instant restoredAt);
}
