package com.aiprovider.mapper.row;

import java.math.BigDecimal;

public class WalkForwardTrainingCandidateRow {
  public String candidateId, parametersJson, trainingRunId, validationRunId;
  public int candidateIndex, tradeCount;
  public BigDecimal metricValue;
}
