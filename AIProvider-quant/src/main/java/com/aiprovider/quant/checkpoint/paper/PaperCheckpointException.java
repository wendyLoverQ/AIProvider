package com.aiprovider.quant.checkpoint.paper;

public final class PaperCheckpointException extends RuntimeException {
    public static final String PAPER_CHECKPOINT_REQUEST_INVALID =
            "PAPER_CHECKPOINT_REQUEST_INVALID";
    public static final String PAPER_CHECKPOINT_CONTEXT_MISMATCH =
            "PAPER_CHECKPOINT_CONTEXT_MISMATCH";
    public static final String PAPER_CHECKPOINT_TIME_INVALID =
            "PAPER_CHECKPOINT_TIME_INVALID";
    public static final String PAPER_CHECKPOINT_STATE_INCONSISTENT =
            "PAPER_CHECKPOINT_STATE_INCONSISTENT";
    public static final String PAPER_CHECKPOINT_RESTORE_INCONSISTENT =
            "PAPER_CHECKPOINT_RESTORE_INCONSISTENT";
    public static final String PAPER_CHECKPOINT_VERSION_CONFLICT =
            "PAPER_CHECKPOINT_VERSION_CONFLICT";
    public static final String PAPER_CHECKPOINT_STORE_FAILED =
            "PAPER_CHECKPOINT_STORE_FAILED";

    private final String errorCode;

    public PaperCheckpointException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaperCheckpointException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
