package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;

public final class ResearchResultComparator {
  private ResearchResultComparator() {}
  public static final java.util.Set<String> METRICS = java.util.Set.of("OOS_TOTAL_RETURN_RATIO", "OOS_MAXIMUM_DRAWDOWN_RATIO", "OOS_TRADE_COUNT", "SUCCESSFUL_OOS_FOLDS", "FAILED_FOLDS", "PARAMETER_CHANGES");

  public static Comparator<ResearchStudyDtos.Summary> comparator(String metric, String direction) {
    String key = metric == null ? "OOS_TOTAL_RETURN_RATIO" : metric.trim().toUpperCase(Locale.ROOT);
    if (!METRICS.contains(key)) throw new ResearchStudyTaskException("RESEARCH_REQUEST_INVALID", "sortBy is invalid");
    String order = direction == null ? defaultDirection(key) : direction.trim().toUpperCase(Locale.ROOT);
    if (!"ASC".equals(order) && !"DESC".equals(order)) throw new ResearchStudyTaskException("RESEARCH_REQUEST_INVALID", "sortDirection is invalid");
    boolean descending = "DESC".equals(order);
    Comparator<ResearchStudyDtos.Summary> value = (left, right) -> {
      Comparable leftValue = metricValue(left, key), rightValue = metricValue(right, key);
      if (leftValue == null && rightValue == null) return 0;
      if (leftValue == null) return 1;
      if (rightValue == null) return -1;
      int result = leftValue.compareTo(rightValue);
      return descending ? -result : result;
    };
    return value.thenComparing(ResearchStudyDtos.Summary::researchStudyId);
  }

  private static String defaultDirection(String key) { return switch (key) { case "OOS_MAXIMUM_DRAWDOWN_RATIO", "PARAMETER_CHANGES", "FAILED_FOLDS" -> "ASC"; default -> "DESC"; }; }
  private static Comparable<?> metricValue(ResearchStudyDtos.Summary summary, String key) {
    return switch (key) {
      case "OOS_TOTAL_RETURN_RATIO" -> summary.oosTotalReturnRatio();
      case "OOS_MAXIMUM_DRAWDOWN_RATIO" -> summary.oosMaximumDrawdownRatio();
      case "OOS_TRADE_COUNT" -> summary.oosTradeCount();
      case "SUCCESSFUL_OOS_FOLDS" -> summary.successfulOosFolds();
      case "FAILED_FOLDS" -> summary.failedFolds();
      case "PARAMETER_CHANGES" -> summary.parameterChanges();
      default -> null;
    };
  }
}
