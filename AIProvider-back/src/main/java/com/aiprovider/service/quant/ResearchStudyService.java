package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ResearchStudyService {
  private static final Pattern COMPARISON_GROUP_KEY = Pattern.compile("[0-9a-fA-F]{64}");
  private static final Set<String> STATUSES = Set.of("QUEUED", "RUNNING", "COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED");
  private final ResearchStudyMapper research;
  private final ObjectMapper json;

  public ResearchStudyService(ResearchStudyMapper research, ObjectMapper json) {
    this.research = research; this.json = json;
  }

  public static String normalizeComparisonGroupKey(String value) {
    if (value == null || value.isBlank()) throw new ResearchStudyTaskException("RESEARCH_REQUEST_INVALID", "comparisonGroupKey is required");
    String normalized = value.trim();
    if (!COMPARISON_GROUP_KEY.matcher(normalized).matches()) throw new ResearchStudyTaskException("RESEARCH_REQUEST_INVALID", "comparisonGroupKey format is invalid");
    return normalized.toLowerCase(Locale.ROOT);
  }

  public BacktestDtos.Page<ResearchStudyDtos.Summary> page(int page, int pageSize, String status, Long datasetId, String strategyCode, String group) {
    validatePage(page, pageSize);
    String cleanStatus = clean(status), cleanStrategy = clean(strategyCode), cleanGroup = group == null ? null : normalizeComparisonGroupKey(group);
    if (cleanStatus != null && !STATUSES.contains(cleanStatus)) throw error("RESEARCH_REQUEST_INVALID", "status is invalid");
    if (datasetId != null && datasetId <= 0) throw error("RESEARCH_REQUEST_INVALID", "datasetId is invalid");
    long offset = offset(page, pageSize);
    List<ResearchStudyRow> rows = research.findPage(cleanStatus, datasetId, cleanStrategy, cleanGroup, pageSize, offset);
    return new BacktestDtos.Page<>(summaries(rows), research.count(cleanStatus, datasetId, cleanStrategy, cleanGroup), page, pageSize);
  }

  public BacktestDtos.Page<ResearchStudyDtos.Summary> results(String group, int page, int pageSize, String sortBy, String sortDirection) {
    String key = normalizeComparisonGroupKey(group);
    validatePage(page, pageSize);
    ResearchResultSort sort = ResearchResultSort.parse(sortBy);
    String direction = sortDirection == null || sortDirection.isBlank() ? sort.defaultDirection() : sortDirection.trim().toUpperCase(Locale.ROOT);
    if (!"ASC".equals(direction) && !"DESC".equals(direction)) throw error("RESEARCH_REQUEST_INVALID", "sortDirection is invalid");
    long offset = offset(page, pageSize);
    List<ResearchStudyRow> rows = research.findComparisonResultsPage(key, sort.name(), "DESC".equals(direction), pageSize, offset);
    return new BacktestDtos.Page<>(summaries(rows), research.countByComparisonGroupKey(key), page, pageSize);
  }

  public ResearchStudyDtos.Detail get(String id) {
    ResearchStudyRow row = require(id);
    return new ResearchStudyDtos.Detail(summary(row), readRanges(row.parameterSpaceJson), readGrid(row.expandedParameterGridJson),
        Instant.ofEpochMilli(row.studyStartOpenTimeMs), Instant.ofEpochMilli(row.studyEndOpenTimeMs), row.trainingBars, row.validationBars,
        row.selectionMetric, row.minimumTrainTrades, row.orderAmount, row.feeRate, row.forceCloseAtEnd);
  }

  public ResearchStudyDtos.ParameterSpaceResponse parameterSpace(String id) {
    ResearchStudyRow row = require(id);
    return new ResearchStudyDtos.ParameterSpaceResponse(row.researchStudyId, row.parameterSpaceMode,
        readRanges(row.parameterSpaceJson), readGrid(row.expandedParameterGridJson), row.candidateCount);
  }

  List<ResearchStudyDtos.Summary> summaries(List<ResearchStudyRow> rows) {
    if (rows == null || rows.isEmpty()) return List.of();
    return rows.stream().map(this::summary).toList();
  }

  private ResearchStudyDtos.Summary summary(ResearchStudyRow row) {
    OosValues oos = oosValues(row);
    return new ResearchStudyDtos.Summary(row.researchStudyId, row.name, row.description, row.datasetId, row.provider, row.marketType, row.dataType, row.symbol, row.intervalCode,
        row.strategyCode, row.strategyVersion, row.executionProfileCode, row.directionMode, row.orderSizingMode, row.evaluationMode, row.parameterSpaceMode, row.candidateCount,
        row.comparisonGroupKey, row.walkForwardStudyId, row.status, row.progressPercent, oos.successfulFolds, oos.failedFolds, oos.hasGaps, oos.totalReturnRatio,
        oos.maximumDrawdownRatio, oos.tradeCount, oos.totalFees, oos.parameterChanges, row.errorCode, row.errorMessage, row.createdAt, row.startedAt, row.finishedAt, row.updatedAt);
  }

  private OosValues oosValues(ResearchStudyRow row) {
    if (!terminal(row.status)) {
      if (row.successfulOosFolds != null || row.failedFolds != null || row.hasOosGaps != null || row.oosTotalReturnRatio != null
          || row.oosMaximumDrawdownRatio != null || row.oosTradeCount != null || row.oosTotalFees != null || row.parameterChanges != null) {
        throw error("RESEARCH_RESULT_INVALID", "non-terminal research study contains OOS results");
      }
      return OosValues.empty();
    }
    requirePresent(row.successfulOosFolds, row.failedFolds, row.hasOosGaps);
    if ("FAILED".equals(row.status) && row.successfulOosFolds == 0) {
      if (row.oosTotalReturnRatio != null || row.oosMaximumDrawdownRatio != null || row.oosTradeCount != null || row.oosTotalFees != null || row.parameterChanges != null) {
        throw error("RESEARCH_RESULT_INVALID", "failed research study contains incomplete OOS results");
      }
      return new OosValues(0, row.failedFolds, true, null, null, null, null, null);
    }
    if (row.oosTotalReturnRatio == null || row.oosMaximumDrawdownRatio == null || row.oosTradeCount == null
        || row.oosTotalFees == null || row.parameterChanges == null) {
      throw error("RESEARCH_RESULT_INVALID", "terminal research study OOS results are incomplete");
    }
    return new OosValues(row.successfulOosFolds, row.failedFolds, row.hasOosGaps, row.oosTotalReturnRatio,
        row.oosMaximumDrawdownRatio, row.oosTradeCount, row.oosTotalFees, row.parameterChanges);
  }

  private void requirePresent(Object... values) { for (Object value : values) if (value == null) throw error("RESEARCH_RESULT_INVALID", "terminal research study OOS results are incomplete"); }
  private ResearchStudyRow require(String id) { if (id == null || id.isBlank()) throw error("RESEARCH_NOT_FOUND", "researchStudyId is required"); ResearchStudyRow row = research.findByResearchStudyId(id); if (row == null) throw error("RESEARCH_NOT_FOUND", "research study not found"); return row; }
  private Map<String, ResearchStudyDtos.IntegerRange> readRanges(String value) { try { return json.readValue(value, new TypeReference<LinkedHashMap<String, ResearchStudyDtos.IntegerRange>>() {}); } catch (Exception e) { throw error("RESEARCH_RESULT_INVALID", "parameter space JSON is invalid"); } }
  private Map<String, List<Integer>> readGrid(String value) { try { return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {}); } catch (Exception e) { throw error("RESEARCH_RESULT_INVALID", "parameter grid JSON is invalid"); } }
  private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase(Locale.ROOT); }
  private void validatePage(int page, int pageSize) { if (page < 1 || pageSize < 1 || pageSize > 100) throw error("RESEARCH_REQUEST_INVALID", "page/pageSize invalid"); }
  private long offset(int page, int pageSize) { try { long value = Math.multiplyExact((long) page - 1, pageSize); if (value > 10_000_000L) throw error("RESEARCH_REQUEST_INVALID", "page offset exceeds limit"); return value; } catch (ArithmeticException e) { throw error("RESEARCH_REQUEST_INVALID", "page offset overflow"); } }
  private boolean terminal(String status) { return Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(status); }
  private ResearchStudyTaskException error(String code, String message) { return new ResearchStudyTaskException(code, message); }
  private record OosValues(Integer successfulFolds, Integer failedFolds, Boolean hasGaps, BigDecimal totalReturnRatio,
                           BigDecimal maximumDrawdownRatio, Integer tradeCount, BigDecimal totalFees, Integer parameterChanges) {
    static OosValues empty() { return new OosValues(null, null, null, null, null, null, null, null); }
  }
}
