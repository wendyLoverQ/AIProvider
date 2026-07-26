package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class WalkForwardStudyOosTest {
  @Test
  void compoundsValidationOnlyAndAggregatesFeesTradesAndParameters() {
    WalkForwardStudyRow study = study("COMPLETED", 2);
    WalkForwardFoldRow first = completedFold(0, "v1", "{\"fastPeriod\":5}");
    WalkForwardFoldRow second = completedFold(1, "v2", "{\"fastPeriod\":7}");
    WalkForwardRunFixture fixture = fixture(study, List.of(first, second), List.of(run("v1", "0.10", "2", 3), run("v2", "-0.10", "1", 4)));
    WalkForwardStudyDtos.OosEquity result = fixture.service.oosEquity("s", 100);

    assertEquals(2, result.successfulFolds());
    assertFalse(result.hasGaps());
    assertEquals(7, fixture.service.get("s").summary().totalOosTradeCount());
    assertEquals(new BigDecimal("3"), fixture.service.get("s").summary().totalOosFees());
    assertEquals(new BigDecimal("-0.01"), result.totalReturnRatio().setScale(2));
    assertEquals(2, result.points().size());
  }

  @Test
  void missingSelectedRunIsOosInvalidInsteadOfZeroSummary() {
    WalkForwardStudyRow study = study("COMPLETED", 1);
    WalkForwardFoldRow fold = completedFold(0, "missing", "{\"fastPeriod\":5}");
    WalkForwardRunFixture fixture = fixture(study, List.of(fold), List.of());
    WalkForwardTaskException error = assertThrows(WalkForwardTaskException.class, () -> fixture.service.oosEquity("s", 100));
    assertEquals("WALK_FORWARD_OOS_INVALID", error.getErrorCode());
  }

  private WalkForwardRunFixture fixture(WalkForwardStudyRow study, List<WalkForwardFoldRow> folds, List<BacktestRunRow> runs) {
    WalkForwardStudyMapper studies = mock(WalkForwardStudyMapper.class); when(studies.findByStudyId("s")).thenReturn(study);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    Map<String, BacktestRunRow> runMap = new LinkedHashMap<>(); for (BacktestRunRow row : runs) runMap.put(row.runId, row);
    Map<String, List<BacktestEquityRow>> equity = new LinkedHashMap<>(); for (BacktestRunRow row : runs) equity.put(row.runId, List.of(equity(row.runId, row.runId.equals("v1") ? 0.0 : 60000.0, row.runId.equals("v1") ? "1" : "0.95")));
    WalkForwardStudySnapshot snapshot = new WalkForwardStudySnapshot(study, folds, Map.of(), runMap, equity);
    when(loader.load(eq(study), anyList(), eq(true))).thenReturn(snapshot);
    when(loader.load(eq(study), anyList(), eq(false))).thenReturn(snapshot);
    WalkForwardStudyService service = new WalkForwardStudyService(studies, mock(WalkForwardFoldMapper.class), loader, new ObjectMapper());
    return new WalkForwardRunFixture(service, studies);
  }

  private WalkForwardStudyRow study(String status, int foldCount) { WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = "s"; row.foldCount = foldCount; row.status = status; row.progressPercent = new BigDecimal("100"); row.parameterGridJson = "{}"; row.finishedAt = Instant.EPOCH; row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH; return row; }
  private WalkForwardFoldRow completedFold(int index, String validation, String parameters) { WalkForwardFoldRow row = new WalkForwardFoldRow(); row.studyId = "s"; row.foldId = "f" + index; row.foldIndex = index; row.status = "COMPLETED"; row.experimentId = "e" + index; row.selectedCandidateId = "c" + index; row.selectedParametersJson = parameters; row.selectedTrainingRunId = "t" + index; row.selectedValidationRunId = validation; return row; }
  private BacktestRunRow run(String id, String returnRatio, String fees, int trades) { BacktestRunRow row = new BacktestRunRow(); row.runId = id; row.status = "COMPLETED"; row.totalReturnRatio = new BigDecimal(returnRatio); row.totalFees = new BigDecimal(fees); row.tradeCount = trades; return row; }
  private BacktestEquityRow equity(String runId, double time, String ratio) { BacktestEquityRow row = new BacktestEquityRow(); row.runId = runId; row.openTimeMs = (long) time; row.pointIndex = 0; row.equityRatio = new BigDecimal(ratio); return row; }
  private record WalkForwardRunFixture(WalkForwardStudyService service, WalkForwardStudyMapper studies) {}
}
