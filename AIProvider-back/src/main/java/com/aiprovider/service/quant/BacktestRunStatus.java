package com.aiprovider.service.quant;

public enum BacktestRunStatus {
    QUEUED, LOADING_SNAPSHOT, RUNNING_ENGINE, PERSISTING, COMPLETED, FAILED;
    public boolean terminal() { return this == COMPLETED || this == FAILED; }
}
