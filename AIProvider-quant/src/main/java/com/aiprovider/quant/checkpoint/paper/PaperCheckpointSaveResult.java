package com.aiprovider.quant.checkpoint.paper;

public final class PaperCheckpointSaveResult {
    private final PaperRuntimeCheckpoint checkpoint;
    private final boolean applied;
    private final Long previousVersion;
    private final long currentVersion;

    public PaperCheckpointSaveResult(
            PaperRuntimeCheckpoint checkpoint,
            boolean applied,
            Long previousVersion,
            long currentVersion) {
        if (checkpoint == null || currentVersion < 0
                || checkpoint.getVersion() != currentVersion) {
            throw invalid("checkpoint and currentVersion must match");
        }
        if (previousVersion != null && previousVersion < 0) {
            throw invalid("previousVersion must be non-negative");
        }
        if (applied && previousVersion != null
                && currentVersion != previousVersion + 1L) {
            throw invalid("applied update must increment previousVersion by one");
        }
        if (applied && previousVersion == null && currentVersion != 0L) {
            throw invalid("first applied save must use version zero");
        }
        if (!applied && (previousVersion == null || previousVersion != currentVersion)) {
            throw invalid("idempotent save must report the current version as previousVersion");
        }
        this.checkpoint = checkpoint;
        this.applied = applied;
        this.previousVersion = previousVersion;
        this.currentVersion = currentVersion;
    }

    public PaperRuntimeCheckpoint getCheckpoint() { return checkpoint; }
    public boolean isApplied() { return applied; }
    public boolean getApplied() { return applied; }
    public Long getPreviousVersion() { return previousVersion; }
    public long getCurrentVersion() { return currentVersion; }

    private static PaperCheckpointException invalid(String message) {
        return new PaperCheckpointException(
                PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID, message);
    }
}
