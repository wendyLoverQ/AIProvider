package com.aiprovider.mapper.row;

import java.math.BigDecimal;
import java.time.Instant;

public class WalkForwardFoldRow {
  public long id;
  public String foldId, studyId;
  public int foldIndex;
  public long trainingStartOpenTimeMs,
      trainingEndOpenTimeMs,
      validationStartOpenTimeMs,
      validationEndOpenTimeMs;
  public String experimentId, status, claimToken, selectedCandidateId, selectedParametersJson;
  public String selectedTrainingRunId, selectedValidationRunId, errorCode, errorMessage;
  public BigDecimal selectionMetricValue, progressPercent;
  public Instant claimedAt, createdAt, updatedAt, startedAt, finishedAt;
}
