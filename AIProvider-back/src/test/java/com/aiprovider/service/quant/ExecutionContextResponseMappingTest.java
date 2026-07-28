package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import com.aiprovider.quant.market.history.port.*;
import com.aiprovider.quant.market.history.service.MarketDataSnapshotService;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class ExecutionContextResponseMappingTest {
  private static final String PROFILE = "USDM_PERPETUAL_LONG_ONLY_1X_V1";

  @Test
  void runAndTradeResponsesExposePersistedExecutionDirection() {
    BacktestRunMapper runs = mock(BacktestRunMapper.class);
    BacktestTradeMapper trades = mock(BacktestTradeMapper.class);
    BacktestRunRow run = run();
    when(runs.findByRunId("r")).thenReturn(run);
    BacktestTradeRow trade = trade();
    when(trades.findPage("r", 100, 0)).thenReturn(List.of(trade));
    when(trades.count("r")).thenReturn(1L);
    BacktestRunService service = runService(runs, trades);

    BacktestDtos.RunDetail detail = service.get("r");
    assertContext(
        detail.executionProfileCode(), detail.directionMode(), detail.orderSizingMode());
    assertNull(detail.initialCapital());
    assertNull(detail.finalEquity());
    BacktestDtos.Trade response = service.trades("r", 1, 100).records().get(0);
    assertEquals("LONG", response.positionSide());
    assertEquals("BUY", response.entryOrderSide());
    assertEquals("SELL", response.exitOrderSide());
  }

  @Test
  void experimentAndStudyResponsesExposePersistedExecutionContext() throws Exception {
    BacktestExperimentService experiments =
        new BacktestExperimentService(
            mock(BacktestExperimentMapper.class),
            mock(BacktestExperimentCandidateMapper.class),
            mock(BacktestRunMapper.class),
            mock(MarketDatasetRepository.class),
            new StrategyRegistry(),
            new BacktestCompatibilityService(new ExecutionProfileRegistry()),
            new ObjectMapper(),
            new QuantExperimentProperties());
    BacktestExperimentRow experiment = experiment();
    BacktestExperimentCandidateRow candidate = new BacktestExperimentCandidateRow();
    candidate.candidateId = "c";
    candidate.experimentId = "e";
    candidate.dispatchStatus = "PENDING";
    candidate.trainingRunId = "t";
    candidate.validationRunId = "v";
    Method summary =
        BacktestExperimentService.class.getDeclaredMethod(
            "summary", BacktestExperimentRow.class, BacktestExperimentSnapshot.class);
    summary.setAccessible(true);
    BacktestExperimentDtos.ExperimentSummary experimentResponse =
        (BacktestExperimentDtos.ExperimentSummary)
            summary.invoke(
                experiments,
                experiment,
                new BacktestExperimentSnapshot(List.of(candidate), Map.of()));
    assertContext(
        experimentResponse.executionProfileCode(),
        experimentResponse.directionMode(),
        experimentResponse.orderSizingMode());
    assertNull(experimentResponse.initialCapital());

    WalkForwardStudyMapper studyMapper = mock(WalkForwardStudyMapper.class);
    WalkForwardFoldMapper foldMapper = mock(WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardStudyService studies =
        new WalkForwardStudyService(studyMapper, foldMapper, loader, new ObjectMapper());
    WalkForwardStudyRow study = study();
    WalkForwardFoldRow fold = new WalkForwardFoldRow();
    fold.studyId = "s";
    fold.foldId = "f";
    fold.foldIndex = 0;
    fold.status = "PENDING";
    WalkForwardStudyDtos.StudySummary studyResponse =
        studies.refreshAggregate(
            new WalkForwardStudySnapshot(study, List.of(fold), Map.of(), Map.of(), Map.of()));
    assertContext(
        studyResponse.executionProfileCode(),
        studyResponse.directionMode(),
        studyResponse.orderSizingMode());
    assertNull(studyResponse.initialCapital());
  }

  private void assertContext(String profile, String direction, String sizing) {
    assertEquals(PROFILE, profile);
    assertEquals("LONG_ONLY", direction);
    assertEquals("BASE_QUANTITY", sizing);
  }

  private BacktestRunService runService(BacktestRunMapper runs, BacktestTradeMapper trades) {
    MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
    StrategyRegistry strategies = new StrategyRegistry();
    return new BacktestRunService(
        runs,
        trades,
        mock(BacktestEquityMapper.class),
        datasets,
        new MarketDataSnapshotService(datasets, mock(MarketCandleRepository.class), 100),
        new BacktestEngine(strategies),
        strategies,
        new BacktestCompatibilityService(new ExecutionProfileRegistry()),
        mock(BacktestPersistenceService.class),
        mock(BacktestFailureService.class),
        mock(ThreadPoolExecutor.class),
        new ObjectMapper());
  }

  private BacktestRunRow run() {
    BacktestRunRow row = new BacktestRunRow();
    row.runId = "r";
    row.executionProfileCode = PROFILE;
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.requestedParametersJson = "{}";
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = BigDecimal.ZERO;
    row.status = "COMPLETED";
    row.progressPercent = new BigDecimal("100");
    row.warningsJson = "[]";
    row.queuedAt = Instant.EPOCH;
    row.updatedAt = Instant.EPOCH;
    return row;
  }

  private BacktestTradeRow trade() {
    BacktestTradeRow row = new BacktestTradeRow();
    row.runId = "r";
    row.tradeNo = 1;
    row.entryTimeMs = 0;
    row.entryPrice = BigDecimal.ONE;
    row.exitTimeMs = 60_000;
    row.exitPrice = BigDecimal.ONE;
    row.amount = BigDecimal.ONE;
    row.grossProfit = BigDecimal.ZERO;
    row.fee = BigDecimal.ZERO;
    row.netProfit = BigDecimal.ZERO;
    row.returnRatio = BigDecimal.ZERO;
    row.positionSide = "LONG";
    row.entryOrderSide = "BUY";
    row.exitOrderSide = "SELL";
    return row;
  }

  private BacktestExperimentRow experiment() {
    BacktestExperimentRow row = new BacktestExperimentRow();
    row.experimentId = "e";
    row.executionProfileCode = PROFILE;
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.parameterGridJson = "{}";
    row.candidateCount = 1;
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = BigDecimal.ZERO;
    row.status = "QUEUED";
    row.createdAt = Instant.EPOCH;
    row.updatedAt = Instant.EPOCH;
    return row;
  }

  private WalkForwardStudyRow study() {
    WalkForwardStudyRow row = new WalkForwardStudyRow();
    row.studyId = "s";
    row.executionProfileCode = PROFILE;
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.parameterGridJson = "{}";
    row.windowMode = "ROLLING";
    row.foldCount = 1;
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = BigDecimal.ZERO;
    row.status = "QUEUED";
    row.progressPercent = BigDecimal.ZERO;
    row.createdAt = Instant.EPOCH;
    row.updatedAt = Instant.EPOCH;
    return row;
  }
}
