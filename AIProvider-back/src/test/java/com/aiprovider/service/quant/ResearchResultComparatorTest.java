package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchResultComparatorTest {
  @Test void sortsMetricDefaultsAndNullsLast() {
    ResearchStudyDtos.Summary high = summary("a", new BigDecimal("0.3"), null, 2, 3, 1);
    ResearchStudyDtos.Summary low = summary("b", new BigDecimal("0.1"), null, 2, 3, 1);
    ResearchStudyDtos.Summary missing = summary("c", null, null, 2, 3, 1);
    assertEquals(List.of("a", "b", "c"), List.of(high, low, missing).stream().sorted(ResearchResultComparator.comparator("OOS_TOTAL_RETURN_RATIO", null)).map(ResearchStudyDtos.Summary::researchStudyId).toList());
    assertEquals(List.of("b", "a", "c"), List.of(high, low, missing).stream().sorted(ResearchResultComparator.comparator("OOS_TOTAL_RETURN_RATIO", "ASC")).map(ResearchStudyDtos.Summary::researchStudyId).toList());
  }

  @Test void tieBreaksByStudyIdAndRejectsUnknownMetric() {
    ResearchStudyDtos.Summary b = summary("b", BigDecimal.ONE, null, 2, 3, 1);
    ResearchStudyDtos.Summary a = summary("a", BigDecimal.ONE, null, 2, 3, 1);
    assertEquals(List.of("a", "b"), List.of(b, a).stream().sorted(ResearchResultComparator.comparator("OOS_TOTAL_RETURN_RATIO", "DESC")).map(ResearchStudyDtos.Summary::researchStudyId).toList());
    assertThrows(ResearchStudyTaskException.class, () -> ResearchResultComparator.comparator("SHARPE_RATIO", null));
  }

  private ResearchStudyDtos.Summary summary(String id, BigDecimal returns, BigDecimal drawdown, int trades, int successful, int changes) {
    return new ResearchStudyDtos.Summary(id, id, null, 1, "BINANCE", "USDM_PERPETUAL", "CANDLE", "BTCUSDT", "1h", "EMA", "1", "PROFILE", "LONG_ONLY", "BASE_QUANTITY", "WALK_FORWARD", "STRATEGY_DEFAULT", 12, new BigDecimal("1000"), "g", "child-" + id, "COMPLETED", BigDecimal.valueOf(100), successful, 0, false, returns, drawdown, trades, BigDecimal.ZERO, changes, null, null, null, null, null, null);
  }
}
