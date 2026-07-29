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
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardOosCalculator {
  private static final MathContext MC = MathContext.DECIMAL128;
  private final ObjectMapper json;

  public WalkForwardOosCalculator(ObjectMapper json) { this.json = json; }

  public WalkForwardOosCalculation calculate(WalkForwardStudyRow study, List<WalkForwardFoldRow> folds,
      Map<String, BacktestRunRow> runs, Map<String, List<BacktestEquityRow>> equities) {
      com.aiprovider.logging.BusinessOperationLogger.start("service.quant.WalkForwardOosCalculator.calculate", new String[] { "study", "folds", "runs", "equities" }, new Object[] { study, folds, runs, equities });
      if (study == null || folds == null || runs == null || equities == null) fail("WALK_FORWARD_OOS_INVALID", "OOS calculation input is incomplete");
    validateStudyAndFolds(study, folds);
    List<WalkForwardFoldRow> successful = folds.stream().filter(fold -> "COMPLETED".equals(fold.status))
        .sorted(Comparator.comparingInt(fold -> fold.foldIndex)).toList();
    int failed = folds.size() - successful.size();
    if (successful.isEmpty()) {
      if ("COMPLETED".equals(study.status) || "COMPLETED_WITH_FAILURES".equals(study.status)) fail("WALK_FORWARD_OOS_INVALID", "terminal study has no successful OOS folds");
      return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardOosCalculator.calculate", new WalkForwardOosCalculation(0, failed, true, null, null, null, null, null, List.of()));
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
    List<WalkForwardStudyDtos.OosPoint> normalizedPoints = points.stream()
        .map(point -> new WalkForwardStudyDtos.OosPoint(point.pointIndex(), point.foldIndex(), point.openTime(),
            WalkForwardOosNumbers.normalize(point.indexRatio()), WalkForwardOosNumbers.normalize(point.drawdownRatio())))
        .toList();
    return com.aiprovider.logging.BusinessOperationLogger.success("service.quant.WalkForwardOosCalculator.calculate", new WalkForwardOosCalculation(successful.size(), failed, failed > 0, trades,
        WalkForwardOosNumbers.normalize(fees), WalkForwardOosNumbers.normalize(previousEnd.subtract(BigDecimal.ONE, MC)),
        WalkForwardOosNumbers.normalize(maximumDrawdown), changes, normalizedPoints));
  }

  WalkForwardOosCalculation calculateForTerminalStatus(WalkForwardStudyRow study, String terminalStatus,
      List<WalkForwardFoldRow> folds, Map<String, BacktestRunRow> runs, Map<String, List<BacktestEquityRow>> equities) {
    WalkForwardStudyRow effective = new WalkForwardStudyRow();
    effective.studyId = study.studyId;
    effective.foldCount = study.foldCount;
    effective.status = terminalStatus;
    return calculate(effective, folds, runs, equities);
  }

  private void validateStudyAndFolds(WalkForwardStudyRow study, List<WalkForwardFoldRow> folds) {
    if (!Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(study.status))
      fail("WALK_FORWARD_STATE_CONFLICT", "study is not terminal");
    if (study.foldCount < 1) fail("WALK_FORWARD_STATE_CONFLICT", "fold count does not match study");
    if (folds.size() != study.foldCount) fail("WALK_FORWARD_STATE_CONFLICT", "fold count does not match study");
    boolean[] seen = new boolean[study.foldCount];
    for (WalkForwardFoldRow fold : folds) {
      if (fold == null || fold.studyId == null || !fold.studyId.equals(study.studyId))
        fail("WALK_FORWARD_STATE_CONFLICT", "fold belongs to another study");
      if (fold.foldIndex < 0 || fold.foldIndex >= study.foldCount)
        fail("WALK_FORWARD_STATE_CONFLICT", "foldIndex is outside study range");
      if (seen[fold.foldIndex]) fail("WALK_FORWARD_STATE_CONFLICT", "duplicate foldIndex");
      seen[fold.foldIndex] = true;
      if (!Set.of("COMPLETED", "FAILED").contains(fold.status))
        fail("WALK_FORWARD_STATE_CONFLICT", "fold state is not terminal");
    }
    for (boolean value : seen) if (!value) fail("WALK_FORWARD_STATE_CONFLICT", "foldIndex coverage is incomplete");
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
