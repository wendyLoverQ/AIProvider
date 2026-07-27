package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalkForwardOosCalculatorTest {
  private final WalkForwardOosCalculator calculator = new WalkForwardOosCalculator(new ObjectMapper());

  @Test void computesGlobalDrawdownAcrossFoldBoundary() {
    WalkForwardStudyRow study = study("COMPLETED", 2);
    WalkForwardFoldRow first = fold(0, "v1", "{\"x\":1}");
    WalkForwardFoldRow second = fold(1, "v2", "{\"x\":2}");
    Map<String, BacktestRunRow> runs = Map.of("v1", run("v1", "1", "1"), "v2", run("v2", "-0.1", "2"));
    Map<String, List<BacktestEquityRow>> equity = new LinkedHashMap<>();
    equity.put("v1", List.of(point("v1", 0, "1"), point("v1", 1, "2.5"), point("v1", 2, "2")));
    equity.put("v2", List.of(point("v2", 3, "1"), point("v2", 4, "0.9")));
    WalkForwardOosCalculation result = calculator.calculate(study, List.of(first, second), runs, equity);
    assertEquals(2, result.successfulFolds()); assertEquals(0, result.failedFolds());
    assertEquals(0, new BigDecimal("0.28").compareTo(result.maximumDrawdownRatio().setScale(2)));
    assertEquals(4, result.tradeCount()); assertEquals(new BigDecimal("3"), result.totalFees());
    assertEquals(1, result.parameterChanges()); assertEquals(5, result.points().size());
    assertThrows(UnsupportedOperationException.class, () -> result.points().add(null));
  }

  @Test void failedWithoutSuccessfulFoldHasNullMetrics() {
    WalkForwardOosCalculation result = calculator.calculate(study("FAILED", 1), List.of(fold("FAILED", 0)), Map.of(), Map.of());
    assertEquals(0, result.successfulFolds()); assertEquals(1, result.failedFolds()); assertTrue(result.hasGaps());
    assertNull(result.totalReturnRatio()); assertNull(result.maximumDrawdownRatio()); assertTrue(result.points().isEmpty());
  }

  @Test void completedWithoutSuccessfulFoldIsInvalid() {
    WalkForwardTaskException error = assertThrows(WalkForwardTaskException.class,
        () -> calculator.calculate(study("COMPLETED", 1), List.of(fold("FAILED", 0)), Map.of(), Map.of()));
    assertEquals("WALK_FORWARD_OOS_INVALID", error.getErrorCode());
  }

  private WalkForwardStudyRow study(String status, int count) { WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = "s"; row.status = status; row.foldCount = count; return row; }
  private WalkForwardFoldRow fold(int index, String runId, String parameters) { WalkForwardFoldRow row = fold("COMPLETED", index); row.selectedCandidateId = "c" + index; row.selectedParametersJson = parameters; row.selectedTrainingRunId = "t" + index; row.selectedValidationRunId = runId; return row; }
  private WalkForwardFoldRow fold(String status, int index) { WalkForwardFoldRow row = new WalkForwardFoldRow(); row.status = status; row.foldIndex = index; return row; }
  private BacktestRunRow run(String id, String returns, String fees) { BacktestRunRow row = new BacktestRunRow(); row.runId = id; row.status = "COMPLETED"; row.totalReturnRatio = new BigDecimal(returns); row.totalFees = new BigDecimal(fees); row.tradeCount = 2; row.maximumDrawdownRatio = new BigDecimal("0.1"); return row; }
  private BacktestEquityRow point(String runId, long time, String ratio) { BacktestEquityRow row = new BacktestEquityRow(); row.runId = runId; row.openTimeMs = time; row.equityRatio = new BigDecimal(ratio); return row; }
}
