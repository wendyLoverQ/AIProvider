package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import java.math.BigDecimal;
import java.util.List;

public record WalkForwardOosCalculation(
    int successfulFolds,
    int failedFolds,
    boolean hasGaps,
    Integer tradeCount,
    BigDecimal totalFees,
    BigDecimal totalReturnRatio,
    BigDecimal maximumDrawdownRatio,
    Integer parameterChanges,
    List<WalkForwardStudyDtos.OosPoint> points) {
  public WalkForwardOosCalculation {
    points = points == null ? List.of() : List.copyOf(points);
  }
}
