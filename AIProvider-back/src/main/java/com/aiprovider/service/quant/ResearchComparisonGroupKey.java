package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.ResearchStudyCreateRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResearchComparisonGroupKey {
  private ResearchComparisonGroupKey() {}

  public static String sha256(ResearchStudyCreateRequest request) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("datasetId", request.getDatasetId());
    values.put("studyStartOpenTimeInclusive", epoch(request.getStudyStartOpenTimeInclusive()));
    values.put("studyEndOpenTimeExclusive", epoch(request.getStudyEndOpenTimeExclusive()));
    values.put("trainingBars", request.getTrainingBars());
    values.put("validationBars", request.getValidationBars());
    values.put("selectionMetric", clean(request.getSelectionMetric()));
    values.put("minimumTrainTrades", request.getMinimumTrainTrades());
    values.put("executionProfileCode", clean(request.getExecutionProfileCode()));
    values.put("directionMode", clean(request.getDirectionMode()));
    values.put("orderSizingMode", clean(request.getOrderSizingMode()));
    values.put("initialCapital", decimal(request.getInitialCapital()));
    values.put("orderAmount", decimal(request.getOrderAmount()));
    values.put("feeRate", decimal(request.getFeeRate()));
    values.put("forceCloseAtEnd", request.isForceCloseAtEnd());
    String canonical = "{" + values.entrySet().stream()
        .map(entry -> quote(entry.getKey()) + ":" + quote(String.valueOf(entry.getValue())))
        .reduce((left, right) -> left + "," + right).orElse("") + "}";
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte value : digest) result.append(String.format("%02x", value));
      return result.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("comparison group hash unavailable", exception);
    }
  }

  private static long epoch(Instant value) { return value.toEpochMilli(); }
  private static String clean(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
  private static String decimal(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }
  private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
