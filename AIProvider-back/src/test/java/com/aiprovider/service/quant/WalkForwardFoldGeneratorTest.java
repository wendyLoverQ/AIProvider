package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.quant.market.history.model.*;
import com.aiprovider.quant.market.model.*;
import com.aiprovider.quant.strategy.StrategyRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalkForwardFoldGeneratorTest {
  @Test
  void generatesAdjacentNonOverlappingRollingFoldsAndDoesNotMutateInput() {
    WalkForwardStudyCreateRequest request = request(0, 130, 40, 30);
    Map<String, List<Integer>> before = new LinkedHashMap<>(request.getParameterGrid());
    WalkForwardFoldGenerator.Result result =
        WalkForwardFoldGenerator.generate(
            request, dataset(130), new StrategyRegistry().get("EMA_CROSS_LONG_ONLY"), 36, 2048);
    assertEquals(3, result.folds().size());
    assertEquals(4, result.candidateCountPerFold());
    assertEquals(24, result.totalChildRuns());
    assertEquals(0, result.folds().get(0).trainingStartOpenTimeMs);
    assertEquals(40 * 60_000L, result.folds().get(0).trainingEndOpenTimeMs);
    assertEquals(
        result.folds().get(0).trainingEndOpenTimeMs,
        result.folds().get(0).validationStartOpenTimeMs);
    assertEquals(
        result.folds().get(0).validationEndOpenTimeMs,
        result.folds().get(1).validationStartOpenTimeMs);
    assertEquals(30 * 60_000L, result.folds().get(1).trainingStartOpenTimeMs);
    assertEquals(130 * 60_000L, result.folds().get(2).validationEndOpenTimeMs);
    assertEquals(before, request.getParameterGrid());
  }

  @Test
  void rejectsUnalignedTailAndInsufficientBars() {
    WalkForwardStudyCreateRequest tail = request(0, 129, 40, 30);
    WalkForwardTaskException tailError =
        assertThrows(
            WalkForwardTaskException.class,
            () ->
                WalkForwardFoldGenerator.generate(
                    tail,
                    dataset(129),
                    new StrategyRegistry().get("EMA_CROSS_LONG_ONLY"),
                    36,
                    2048));
    assertEquals("WALK_FORWARD_WINDOW_INVALID", tailError.getErrorCode());
    WalkForwardStudyCreateRequest bars = request(0, 50, 20, 20);
    WalkForwardTaskException barsError =
        assertThrows(
            WalkForwardTaskException.class,
            () ->
                WalkForwardFoldGenerator.generate(
                    bars,
                    dataset(50),
                    new StrategyRegistry().get("EMA_CROSS_LONG_ONLY"),
                    36,
                    2048));
    assertEquals("WALK_FORWARD_WINDOW_INVALID", barsError.getErrorCode());
  }

  @Test
  void enforcesFoldAndChildRunCapacity() {
    WalkForwardStudyCreateRequest request = request(0, 100, 20, 20);
    WalkForwardTaskException error =
        assertThrows(
            WalkForwardTaskException.class,
            () ->
                WalkForwardFoldGenerator.generate(
                    request,
                    dataset(100),
                    new StrategyRegistry().get("EMA_CROSS_LONG_ONLY"),
                    2,
                    2048));
    assertEquals("WALK_FORWARD_TOO_LARGE", error.getErrorCode());
  }

  private WalkForwardStudyCreateRequest request(
      long startBars, long endBars, int trainingBars, int validationBars) {
    WalkForwardStudyCreateRequest request = new WalkForwardStudyCreateRequest();
    request.setDatasetId(1);
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode("USDM_PERPETUAL_LONG_ONLY_1X_V1");
    request.setDirectionMode("LONG_ONLY");
    request.setOrderSizingMode("BASE_QUANTITY");
    request.setParameterGrid(
        new LinkedHashMap<>(Map.of("fastPeriod", List.of(5, 7), "slowPeriod", List.of(20, 25))));
    request.setStudyStartOpenTimeInclusive(Instant.ofEpochMilli(startBars * 60_000L));
    request.setStudyEndOpenTimeExclusive(Instant.ofEpochMilli(endBars * 60_000L));
    request.setTrainingBars(trainingBars);
    request.setValidationBars(validationBars);
    request.setSelectionMetric("TRAIN_TOTAL_RETURN_RATIO");
    request.setMinimumTrainTrades(0);
    request.setOrderAmount(BigDecimal.ONE);
    request.setFeeRate(new BigDecimal("0.001"));
    request.setForceCloseAtEnd(true);
    return request;
  }

  private MarketDataset dataset(long bars) {
    MarketDataset d = new MarketDataset();
    d.setProvider(MarketProviderId.BINANCE_USDM);
    d.setMarketType(MarketType.USDM_PERPETUAL);
    d.setDataType(MarketDataType.CANDLE);
    d.setSymbol("BTCUSDT");
    d.setInterval(KlineInterval.M1);
    d.setStatus(MarketDatasetStatus.CONTIGUOUS);
    d.setEarliestOpenTime(Instant.EPOCH);
    d.setLatestOpenTime(Instant.ofEpochMilli((bars - 1) * 60_000L));
    d.setCandleCount(bars);
    d.setLastValidatedAt(Instant.EPOCH);
    return d;
  }
}
