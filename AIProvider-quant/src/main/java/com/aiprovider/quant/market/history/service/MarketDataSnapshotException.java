package com.aiprovider.quant.market.history.service;

/** Stable failure contract for loading a backtest input snapshot. */
public class MarketDataSnapshotException extends RuntimeException {
    public enum ErrorCode {
        SNAPSHOT_REQUEST_INVALID, SNAPSHOT_DATASET_NOT_FOUND, SNAPSHOT_DATASET_INVALID,
        SNAPSHOT_DATASET_NOT_READY, SNAPSHOT_RANGE_NOT_ALIGNED, SNAPSHOT_RANGE_NOT_COVERED,
        SNAPSHOT_TOO_LARGE, SNAPSHOT_COUNT_MISMATCH, SNAPSHOT_QUERY_OVERFLOW, SNAPSHOT_DATA_INVALID
    }

    private final ErrorCode errorCode;

    public MarketDataSnapshotException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
