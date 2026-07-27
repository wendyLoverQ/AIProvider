package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.config.quant.QuantWalkForwardProperties;
import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.mapper.*;
import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import com.aiprovider.quant.market.history.model.*;
import com.aiprovider.quant.market.history.port.MarketCandleRepository;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.service.MarketDataSnapshotService;
import com.aiprovider.quant.market.model.*;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class ExecutionContextChainValidationTest {
  private static final String PROFILE = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
  private final MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
  private final StrategyRegistry strategies = new StrategyRegistry();
  private final BacktestCompatibilityService compatibility =
      new BacktestCompatibilityService(new ExecutionProfileRegistry());
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void allThreeCreationChainsRejectUnknownAndCaseChangedExecutionValues() {
    when(datasets.findById(1)).thenReturn(dataset());

    assertAllChains("UNKNOWN", "LONG_ONLY", "BASE_QUANTITY",
        "BACKTEST_EXECUTION_PROFILE_NOT_SUPPORTED");
    assertAllChains(PROFILE, "long_only", "BASE_QUANTITY",
        "BACKTEST_DIRECTION_INCOMPATIBLE");
    assertAllChains(PROFILE, "LONG_ONLY", "base_quantity",
        "BACKTEST_ORDER_SIZING_INCOMPATIBLE");
  }

  private void assertAllChains(String profile, String direction, String sizing, String code) {
    BacktestTaskException runFailure =
        assertThrows(
            BacktestTaskException.class,
            () ->
                runService()
                    .createWithRunId(
                        UUID.randomUUID().toString(), runRequest(profile, direction, sizing)));
    assertEquals(code, runFailure.getErrorCode());

    BacktestTaskException experimentFailure =
        assertThrows(
            BacktestTaskException.class,
            () ->
                experimentService()
                    .createWithExperimentId(
                        UUID.randomUUID().toString(),
                        experimentRequest(profile, direction, sizing)));
    assertEquals(code, experimentFailure.getErrorCode());

    WalkForwardTaskException studyFailure =
        assertThrows(
            WalkForwardTaskException.class,
            () -> walkForwardService().create(studyRequest(profile, direction, sizing)));
    assertEquals(code, studyFailure.getErrorCode());
  }

  private BacktestRunService runService() {
    return new BacktestRunService(
        mock(BacktestRunMapper.class),
        mock(BacktestTradeMapper.class),
        mock(BacktestEquityMapper.class),
        datasets,
        new MarketDataSnapshotService(datasets, mock(MarketCandleRepository.class), 100),
        new BacktestEngine(strategies),
        strategies,
        compatibility,
        mock(BacktestPersistenceService.class),
        mock(BacktestFailureService.class),
        mock(ThreadPoolExecutor.class),
        json);
  }

  private BacktestExperimentService experimentService() {
    return new BacktestExperimentService(
        mock(BacktestExperimentMapper.class),
        mock(BacktestExperimentCandidateMapper.class),
        mock(BacktestRunMapper.class),
        datasets,
        strategies,
        compatibility,
        json,
        new QuantExperimentProperties());
  }

  private WalkForwardStudyCreationService walkForwardService() {
    return new WalkForwardStudyCreationService(
        mock(WalkForwardStudyMapper.class),
        mock(WalkForwardFoldMapper.class),
        datasets,
        strategies,
        compatibility,
        json,
        new QuantWalkForwardProperties());
  }

  private BacktestCreateRequest runRequest(String profile, String direction, String sizing) {
    BacktestCreateRequest request = new BacktestCreateRequest();
    request.setDatasetId(1);
    request.setStartOpenTimeInclusive(Instant.EPOCH);
    request.setEndOpenTimeExclusive(Instant.ofEpochMilli(600_000));
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode(profile);
    request.setDirectionMode(direction);
    request.setOrderSizingMode(sizing);
    request.setStrategyParameters(Map.of("fastPeriod", 2, "slowPeriod", 4));
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(BigDecimal.ZERO);
    request.setForceCloseAtEnd(true);
    return request;
  }

  private BacktestExperimentCreateRequest experimentRequest(
      String profile, String direction, String sizing) {
    BacktestExperimentCreateRequest request = new BacktestExperimentCreateRequest();
    request.setDatasetId(1);
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode(profile);
    request.setDirectionMode(direction);
    request.setOrderSizingMode(sizing);
    request.setParameterGrid(Map.of("fastPeriod", List.of(2), "slowPeriod", List.of(4)));
    request.setTrainingStartOpenTimeInclusive(Instant.EPOCH);
    request.setTrainingEndOpenTimeExclusive(Instant.ofEpochMilli(300_000));
    request.setValidationStartOpenTimeInclusive(Instant.ofEpochMilli(300_000));
    request.setValidationEndOpenTimeExclusive(Instant.ofEpochMilli(600_000));
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(BigDecimal.ZERO);
    request.setForceCloseAtEnd(true);
    return request;
  }

  private WalkForwardStudyCreateRequest studyRequest(
      String profile, String direction, String sizing) {
    WalkForwardStudyCreateRequest request = new WalkForwardStudyCreateRequest();
    request.setDatasetId(1);
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode(profile);
    request.setDirectionMode(direction);
    request.setOrderSizingMode(sizing);
    request.setParameterGrid(Map.of("fastPeriod", List.of(2), "slowPeriod", List.of(4)));
    request.setStudyStartOpenTimeInclusive(Instant.EPOCH);
    request.setStudyEndOpenTimeExclusive(Instant.ofEpochMilli(600_000));
    request.setTrainingBars(5);
    request.setValidationBars(5);
    request.setSelectionMetric("TRAIN_TOTAL_RETURN_RATIO");
    request.setMinimumTrainTrades(0);
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(BigDecimal.ZERO);
    request.setForceCloseAtEnd(true);
    return request;
  }

  private MarketDataset dataset() {
    MarketDataset dataset = new MarketDataset();
    dataset.setId(1);
    dataset.setProvider(MarketProviderId.BINANCE_USDM);
    dataset.setMarketType(MarketType.USDM_PERPETUAL);
    dataset.setDataType(MarketDataType.CANDLE);
    dataset.setSymbol("BTCUSDT");
    dataset.setInterval(KlineInterval.M1);
    dataset.setEarliestOpenTime(Instant.EPOCH);
    dataset.setLatestOpenTime(Instant.ofEpochMilli(540_000));
    dataset.setCandleCount(10);
    dataset.setExpectedInsideRange(10);
    dataset.setGapCount(0);
    dataset.setGapSegmentCount(0);
    dataset.setStatus(MarketDatasetStatus.CONTIGUOUS);
    dataset.setLastValidatedAt(Instant.EPOCH);
    return dataset;
  }
}
