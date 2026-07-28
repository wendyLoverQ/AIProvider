package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import com.aiprovider.quant.market.history.model.*;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.*;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BacktestExperimentFixedIdTest {
  private static final String PROFILE = "USDM_PERPETUAL_LONG_ONLY_1X_V1";

  @Test
  void sameExecutionContextIsIdempotentAndEachDifferentFieldConflicts() {
    String experimentId = UUID.randomUUID().toString();
    BacktestExperimentMapper experiments = mock(BacktestExperimentMapper.class);
    BacktestExperimentRow existing = existing(experimentId);
    when(experiments.findByExperimentId(experimentId)).thenReturn(existing);
    MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
    when(datasets.findById(1)).thenReturn(dataset());
    BacktestExperimentCandidateMapper candidates = mock(BacktestExperimentCandidateMapper.class);
    BacktestExperimentService service =
        new BacktestExperimentService(
            experiments,
            candidates,
            mock(BacktestRunMapper.class),
            datasets,
            new StrategyRegistry(),
            new BacktestCompatibilityService(new ExecutionProfileRegistry()),
            new ObjectMapper(),
            new QuantExperimentProperties());

    assertEquals(
        experimentId, service.createWithExperimentId(experimentId, request()).experimentId());
    verify(experiments, never()).insert(any());
    verify(candidates, never()).insertBatch(any());

    BacktestExperimentCreateRequest profileChanged = request();
    profileChanged.setExecutionProfileCode("UNKNOWN");
    assertConflict(service, experimentId, profileChanged);

    BacktestExperimentCreateRequest directionChanged = request();
    directionChanged.setDirectionMode("long_only");
    assertConflict(service, experimentId, directionChanged);

    BacktestExperimentCreateRequest sizingChanged = request();
    sizingChanged.setOrderSizingMode("base_quantity");
    assertConflict(service, experimentId, sizingChanged);
  }

  private void assertConflict(
      BacktestExperimentService service,
      String experimentId,
      BacktestExperimentCreateRequest request) {
    BacktestTaskException failure =
        assertThrows(
            BacktestTaskException.class,
            () -> service.createWithExperimentId(experimentId, request));
    assertEquals("WALK_FORWARD_EXPERIMENT_CONFLICT", failure.getErrorCode());
  }

  private BacktestExperimentCreateRequest request() {
    BacktestExperimentCreateRequest request = new BacktestExperimentCreateRequest();
    request.setDatasetId(1);
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode(PROFILE);
    request.setDirectionMode("LONG_ONLY");
    request.setOrderSizingMode("BASE_QUANTITY");
    LinkedHashMap<String, List<Integer>> grid = new LinkedHashMap<>();
    grid.put("fastPeriod", List.of(2));
    grid.put("slowPeriod", List.of(4));
    request.setParameterGrid(grid);
    request.setTrainingStartOpenTimeInclusive(Instant.EPOCH);
    request.setTrainingEndOpenTimeExclusive(Instant.ofEpochMilli(300_000));
    request.setValidationStartOpenTimeInclusive(Instant.ofEpochMilli(300_000));
    request.setValidationEndOpenTimeExclusive(Instant.ofEpochMilli(600_000));
    request.setInitialCapital(new BigDecimal("1000"));
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(BigDecimal.ZERO);
    request.setForceCloseAtEnd(true);
    return request;
  }

  private BacktestExperimentRow existing(String experimentId) {
    BacktestExperimentRow row = new BacktestExperimentRow();
    row.experimentId = experimentId;
    row.datasetId = 1;
    row.strategyCode = "EMA_CROSS_LONG_ONLY";
    row.strategyVersion = "1.0.0";
    row.executionProfileCode = PROFILE;
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.parameterGridJson = "{\"fastPeriod\":[2],\"slowPeriod\":[4]}";
    row.candidateCount = 1;
    row.trainingStartOpenTimeMs = 0;
    row.trainingEndOpenTimeMs = 300_000;
    row.validationStartOpenTimeMs = 300_000;
    row.validationEndOpenTimeMs = 600_000;
    row.initialCapital = new BigDecimal("1000");
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = BigDecimal.ZERO;
    row.forceCloseAtEnd = true;
    return row;
  }

  private MarketDataset dataset() {
    MarketDataset dataset = new MarketDataset();
    dataset.setProvider(MarketProviderId.BINANCE_USDM);
    dataset.setMarketType(MarketType.USDM_PERPETUAL);
    dataset.setDataType(MarketDataType.CANDLE);
    dataset.setSymbol("BTCUSDT");
    dataset.setInterval(KlineInterval.M1);
    dataset.setEarliestOpenTime(Instant.EPOCH);
    dataset.setLatestOpenTime(Instant.ofEpochMilli(540_000));
    dataset.setCandleCount(10);
    dataset.setGapCount(0);
    dataset.setGapSegmentCount(0);
    dataset.setStatus(MarketDatasetStatus.CONTIGUOUS);
    dataset.setLastValidatedAt(Instant.EPOCH);
    return dataset;
  }
}
