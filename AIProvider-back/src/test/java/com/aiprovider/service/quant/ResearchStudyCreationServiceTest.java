package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiprovider.config.quant.QuantResearchProperties;
import com.aiprovider.controller.quant.dto.ResearchStudyCreateRequest;
import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.quant.market.history.model.MarketDataType;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResearchStudyCreationServiceTest {
  @Test
  void childWalkForwardUsesTheParentInitialCapital() {
    ResearchStudyMapper research = mock(ResearchStudyMapper.class);
    WalkForwardStudyCreationService walkForward = mock(WalkForwardStudyCreationService.class);
    MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
    when(research.insert(any())).thenReturn(1);
    when(datasets.findById(1)).thenReturn(dataset());
    when(walkForward.createWithStudyId(anyString(), any()))
        .thenReturn(new WalkForwardStudyDtos.CreateResponse("child", 1, 1, 2));

    new ResearchStudyCreationService(
            research,
            walkForward,
            datasets,
            new StrategyRegistry(),
            new ObjectMapper(),
            new QuantResearchProperties())
        .create(request());

    ArgumentCaptor<WalkForwardStudyCreateRequest> child =
        ArgumentCaptor.forClass(WalkForwardStudyCreateRequest.class);
    verify(walkForward).createWithStudyId(anyString(), child.capture());
    assertEquals(new BigDecimal("1000.000000000000000001"), child.getValue().getInitialCapital());
  }

  private ResearchStudyCreateRequest request() {
    ResearchStudyCreateRequest request = new ResearchStudyCreateRequest();
    request.setName("capital inheritance");
    request.setDatasetId(1);
    request.setStrategyCode("EMA_CROSS_LONG_ONLY");
    request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode("USDM_PERPETUAL_LONG_ONLY_1X_V1");
    request.setDirectionMode("LONG_ONLY");
    request.setOrderSizingMode("BASE_QUANTITY");
    request.setEvaluationMode("WALK_FORWARD");
    request.setParameterSpaceMode("STRATEGY_DEFAULT");
    request.setStudyStartOpenTimeInclusive(Instant.EPOCH);
    request.setStudyEndOpenTimeExclusive(Instant.ofEpochMilli(600_000));
    request.setTrainingBars(5);
    request.setValidationBars(5);
    request.setSelectionMetric("TRAIN_TOTAL_RETURN_RATIO");
    request.setMinimumTrainTrades(0);
    request.setInitialCapital(new BigDecimal("1000.000000000000000001"));
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
    return dataset;
  }
}
