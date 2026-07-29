package com.aiprovider.quant.checkpoint.paper;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPaperCheckpointStore implements PaperCheckpointStore {
    private final ConcurrentHashMap<String, PaperRuntimeCheckpoint> checkpoints =
            new ConcurrentHashMap<>();

    @Override
    public PaperCheckpointSaveResult save(
            PaperRuntimeCheckpoint checkpoint,
            OptionalLong expectedPreviousVersion) {
        if (checkpoint == null || expectedPreviousVersion == null) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "checkpoint and expectedPreviousVersion are required");
        }
        SaveHolder holder = new SaveHolder();
        checkpoints.compute(checkpoint.getSessionId(), (sessionId, current) -> {
            if (current == null) {
                if (expectedPreviousVersion.isPresent() || checkpoint.getVersion() != 0L) {
                    throw conflict("first save requires no previous version and checkpoint version 0");
                }
                holder.result = new PaperCheckpointSaveResult(checkpoint, true, null, 0L);
                return checkpoint;
            }
            if (current.equals(checkpoint)) {
                holder.result = new PaperCheckpointSaveResult(
                        current, false, current.getVersion(), current.getVersion());
                return current;
            }
            if (!expectedPreviousVersion.isPresent()
                    || expectedPreviousVersion.getAsLong() != current.getVersion()
                    || checkpoint.getVersion() != current.getVersion() + 1L) {
                throw conflict("checkpoint version conflict: current=" + current.getVersion()
                        + ", expected=" + describe(expectedPreviousVersion)
                        + ", incoming=" + checkpoint.getVersion());
            }
            holder.result = new PaperCheckpointSaveResult(
                    checkpoint, true, current.getVersion(), checkpoint.getVersion());
            return checkpoint;
        });
        if (holder.result == null) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_STORE_FAILED,
                    "checkpoint save did not produce a result");
        }
        return holder.result;
    }

    @Override
    public Optional<PaperRuntimeCheckpoint> loadLatest(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw error(PaperCheckpointException.PAPER_CHECKPOINT_REQUEST_INVALID,
                    "sessionId is required");
        }
        return Optional.ofNullable(checkpoints.get(sessionId));
    }

    private static PaperCheckpointException conflict(String message) {
        return error(PaperCheckpointException.PAPER_CHECKPOINT_VERSION_CONFLICT, message);
    }

    private static PaperCheckpointException error(String code, String message) {
        return new PaperCheckpointException(code, message);
    }

    private static String describe(OptionalLong version) {
        return version.isPresent() ? Long.toString(version.getAsLong()) : "empty";
    }

    private static final class SaveHolder {
        private PaperCheckpointSaveResult result;
    }
}
