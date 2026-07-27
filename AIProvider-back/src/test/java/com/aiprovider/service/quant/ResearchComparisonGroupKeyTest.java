package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.ResearchStudyCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchComparisonGroupKeyTest {
  @Test void fixedVectorAndDecimalNormalizationAreStable() {
    ResearchStudyCreateRequest request = request();
    assertEquals("99c0fea4480804b2e43f3c3a8db454d90d1b030c0bbe615c86ae3e18c56f9c4f", ResearchComparisonGroupKey.sha256(request));
    request.setOrderAmount(new BigDecimal("0.010")); request.setFeeRate(new BigDecimal("0.000400"));
    assertEquals(ResearchComparisonGroupKey.sha256(request()), ResearchComparisonGroupKey.sha256(request));
  }

  @Test void namesStrategiesAndParameterSpaceDoNotChangeGroupButContextDoes() {
    ResearchStudyCreateRequest first = request();
    ResearchStudyCreateRequest second = request();
    second.setName("other"); second.setDescription("other"); second.setStrategyCode("OTHER"); second.setStrategyVersion("9");
    assertEquals(ResearchComparisonGroupKey.sha256(first), ResearchComparisonGroupKey.sha256(second));
    second.setFeeRate(new BigDecimal("0.0005"));
    assertNotEquals(ResearchComparisonGroupKey.sha256(first), ResearchComparisonGroupKey.sha256(second));
  }

  @Test void normalizesOnlyTrimmedSixtyFourDigitHexKeys() {
    String key = "A".repeat(64);
    assertEquals("a".repeat(64), ResearchStudyService.normalizeComparisonGroupKey("  " + key + "  "));
    assertThrows(ResearchStudyTaskException.class, () -> ResearchStudyService.normalizeComparisonGroupKey("a".repeat(63)));
    assertThrows(ResearchStudyTaskException.class, () -> ResearchStudyService.normalizeComparisonGroupKey("g" + "a".repeat(63)));
  }

  private ResearchStudyCreateRequest request() {
    ResearchStudyCreateRequest request = new ResearchStudyCreateRequest();
    request.setName("test"); request.setDatasetId(1); request.setStrategyCode("EMA_CROSS_LONG_ONLY"); request.setStrategyVersion("1.0.0");
    request.setExecutionProfileCode("USDM_PERPETUAL_LONG_ONLY_1X_V1"); request.setDirectionMode("LONG_ONLY"); request.setOrderSizingMode("BASE_QUANTITY");
    request.setEvaluationMode("WALK_FORWARD"); request.setParameterSpaceMode("STRATEGY_DEFAULT"); request.setStudyStartOpenTimeInclusive(Instant.parse("2025-01-01T00:00:00Z")); request.setStudyEndOpenTimeExclusive(Instant.parse("2026-01-01T00:00:00Z"));
    request.setTrainingBars(720); request.setValidationBars(240); request.setSelectionMetric("TRAIN_TOTAL_RETURN_RATIO"); request.setMinimumTrainTrades(5); request.setOrderAmount(new BigDecimal("0.01")); request.setFeeRate(new BigDecimal("0.0004")); request.setForceCloseAtEnd(true);
    return request;
  }
}
