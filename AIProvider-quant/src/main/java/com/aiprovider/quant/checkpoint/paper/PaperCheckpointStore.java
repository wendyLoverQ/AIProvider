package com.aiprovider.quant.checkpoint.paper;

import java.util.Optional;
import java.util.OptionalLong;

public interface PaperCheckpointStore {
    PaperCheckpointSaveResult save(
            PaperRuntimeCheckpoint checkpoint,
            OptionalLong expectedPreviousVersion);

    Optional<PaperRuntimeCheckpoint> loadLatest(String sessionId);
}
