package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardOosCalculator {
  private static final MathContext MC = MathContext.DECIMAL128;
  private final ObjectMapper json;

  public WalkForwardOosCalculator(ObjectMapper json) { this.json = json; }

  public WalkForwardOosCalculation calculate(WalkForwardStudyRow study, List<WalkForwardFoldRow> folds,
      Map<String, BacktestRunRow> runs, Map<String, List<BacktestEquityRow>> equities) {
    if (study == null || folds == null || runs == null || equities == null) fail("WALK_FORWARD_OOS_INVALID", "OOS calculation input is incomplete");
    List<WalkForwardFoldRow> successful = folds.stream().filter(fold -> "COMPLETED".equals(fold.status))
        .sorted(Comparator.comparingInt(fold -> fold.foldIndex)).toList();
    int failed = folds.size() - successful.size();
    if (successful.isEmpty()) {
      if ("COMPLETED".equals(study.status) || "COMPLETED_WITH_FAILURES".equals(study.status)) fail("WALK_FORWARD_OOS_INVALID", "terminal study has no successful OOS folds");
      return new WalkForwardOosCalculation(0, failed, true, null, null, null, null, null, List.of());
    }
    BigDecimal previousEnd = BigDecimal.ONE, runningPeak = BigDecimal.ONE;
    BigDecimal fees = BigDecimal.ZERO, maximumDrawdown = BigDecimal.ZERO;
    int trades = 0, changes = 0, pointIndex = 0;
    Instant previousTime = null;
    Map<String, Integer> previousParameters = null;
    List<WalkForwardStudyDtos.OosPoint> points = new ArrayList<>();
    for (WalkForwardFoldRow fold : successful) {
      validateSelectedFields(fold);
      BacktestRunRow run = runs.get(fold.selectedValidationRunId);
      if (run == null || !"COMPLETED".equals(run.status) || run.tradeCount == null || run.totalFees == null
          || run.totalReturnRatio == null || run.maximumDrawdownRatio == null) {
        fail("WALK_FORWARD_OOS_INVALID", "selected validation run metrics are incomplete");
      }
      List<BacktestEquityRow> raw = equities.getOrDefault(fold.selectedValidationRunId, List.of());
      if (raw.isEmpty()) fail("WALK_FORWARD_OOS_INVALID", "selected validation equity is empty");
      BigDecimal firstEquity = raw.get(0).equityRatio;
      if (firstEquity == null || firstEquity.signum() <= 0) fail("WALK_FORWARD_OOS_INVALID", "first equity must be positive");
      Instant foldPrevious = null;
      for (BacktestEquityRow value : raw) {
        if (value.equityRatio == null || value.equityRatio.signum() <= 0) fail("WALK_FORWARD_OOS_INVALID", "equity ratio must be positive");
        Instant time = Instant.ofEpochMilli(value.openTimeMs);
        if (foldPrevious != null && !time.isAfter(foldPrevious)) fail("WALK_FORWARD_OOS_INVALID", "equity time is not strictly increasing in fold");
        if (previousTime != null && !time.isAfter(previousTime)) fail("WALK_FORWARD_OOS_INVALID", "equity time is not strictly increasing across folds");
        BigDecimal normalized = previousEnd.multiply(value.equityRatio.divide(firstEquity, MC), MC);
        if (normalized.signum() <= 0) fail("WALK_FORWARD_OOS_INVALID", "normalized equity is not positive");
        runningPeak = runningPeak.max(normalized);
        BigDecimal drawdown = runningPeak.subtract(normalized, MC).divide(runningPeak, MC);
        if (drawdown.signum() < 0 || drawdown.compareTo(BigDecimal.ONE) > 0) fail("WALK_FORWARD_OOS_INVALID", "drawdown is outside [0,1]");
        points.add(new WalkForwardStudyDtos.OosPoint(pointIndex++, fold.foldIndex, time, normalized, drawdown));
        maximumDrawdown = maximumDrawdown.max(drawdown);
        foldPrevious = time; previousTime = time;
      }
      previousEnd = points.get(points.size() - 1).indexRatio();
      fees = fees.add(run.totalFees, MC);
      trades = Math.addExact(trades, run.tradeCount);
      Map<String, Integer> parameters = readParameters(fold.selectedParametersJson);
      if (previousParameters != null && !previousParameters.equals(parameters)) changes++;
      previousParameters = parameters;
    }
    return new WalkForwardOosCalculation(successful.size(), failed, failed > 0, trades, fees,
        previousEnd.subtract(BigDecimal.ONE, MC), maximumDrawdown, changes, points);
  }

  private void validateSelectedFields(WalkForwardFoldRow fold) {
    if (fold.selectedCandidateId == null || fold.selectedParametersJson == null || fold.selectedTrainingRunId == null || fold.selectedValidationRunId == null)
      fail("WALK_FORWARD_OOS_INVALID", "completed fold selected fields are incomplete");
  }

  private Map<String, Integer> readParameters(String value) {
    try { return json.readValue(value, new TypeReference<LinkedHashMap<String, Integer>>() {}); }
    catch (Exception exception) { fail("WALK_FORWARD_OOS_INVALID", "stored parameters JSON is invalid"); return Map.of(); }
  }

  private void fail(String code, String message) { throw new WalkForwardTaskException(code, message); }
}
