package com.aiprovider.service.quant;

import java.util.Locale;

public enum ResearchResultSort {
  OOS_TOTAL_RETURN_RATIO("DESC"),
  OOS_MAXIMUM_DRAWDOWN_RATIO("ASC"),
  OOS_TRADE_COUNT("DESC"),
  SUCCESSFUL_OOS_FOLDS("DESC"),
  FAILED_FOLDS("ASC"),
  PARAMETER_CHANGES("ASC");

  private final String defaultDirection;

  ResearchResultSort(String defaultDirection) { this.defaultDirection = defaultDirection; }
  public String defaultDirection() { return defaultDirection; }

  public static ResearchResultSort parse(String value) {
    String normalized = value == null || value.isBlank() ? "OOS_TOTAL_RETURN_RATIO" : value.trim().toUpperCase(Locale.ROOT);
    try { return valueOf(normalized); }
    catch (IllegalArgumentException exception) { throw new ResearchStudyTaskException("RESEARCH_REQUEST_INVALID", "sortBy is invalid"); }
  }
}
