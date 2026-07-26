package com.aiprovider.mapper.row;

import java.time.Instant;

public class BacktestExperimentCandidateRow {
    public long id;
    public String candidateId, experimentId, parametersJson, trainingRunId, validationRunId, dispatchStatus;
    public int candidateIndex;
    public String claimToken, errorCode, errorMessage;
    public Instant claimedAt, createdAt, updatedAt;
}
