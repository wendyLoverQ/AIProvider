package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ResearchStudyService {
  private final ResearchStudyMapper research;
  private final WalkForwardStudyMapper walkForward;
  private final WalkForwardStudyService walkForwardService;
  private final ObjectMapper json;

  public ResearchStudyService(ResearchStudyMapper research, WalkForwardStudyMapper walkForward,
      WalkForwardStudyService walkForwardService, ObjectMapper json) {
    this.research = research; this.walkForward = walkForward; this.walkForwardService = walkForwardService; this.json = json;
  }

  public BacktestDtos.Page<ResearchStudyDtos.Summary> page(int page, int pageSize, String status, Long datasetId, String strategyCode, String group) {
    validatePage(page, pageSize);
    String cleanStatus = clean(status), cleanStrategy = clean(strategyCode), cleanGroup = clean(group);
    if (cleanStatus != null && !Set.of("QUEUED", "RUNNING", "COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(cleanStatus)) throw error("RESEARCH_REQUEST_INVALID", "status is invalid");
    if (datasetId != null && datasetId <= 0) throw error("RESEARCH_REQUEST_INVALID", "datasetId is invalid");
    long offset = (long) (page - 1) * pageSize;
    List<ResearchStudyRow> rows = research.findPage(cleanStatus, datasetId, cleanStrategy, cleanGroup, pageSize, offset);
    return new BacktestDtos.Page<>(summaries(rows), research.count(cleanStatus, datasetId, cleanStrategy, cleanGroup), page, pageSize);
  }

  public BacktestDtos.Page<ResearchStudyDtos.Summary> results(String group, int page, int pageSize, String sortBy, String sortDirection) {
    String key = clean(group);
    if (key == null) throw error("RESEARCH_REQUEST_INVALID", "comparisonGroupKey is required");
    validatePage(page, pageSize);
    List<ResearchStudyRow> rows = research.findAllByComparisonGroupKey(key);
    List<ResearchStudyDtos.Summary> values = new ArrayList<>(summaries(rows));
    values.sort(ResearchResultComparator.comparator(sortBy, sortDirection));
    int from = Math.min((page - 1) * pageSize, values.size()), to = Math.min(from + pageSize, values.size());
    return new BacktestDtos.Page<>(values.subList(from, to), values.size(), page, pageSize);
  }

  public ResearchStudyDtos.Detail get(String id) {
    ResearchStudyRow row = require(id);
    ResearchStudyDtos.Summary summary = summaries(List.of(row)).get(0);
    return new ResearchStudyDtos.Detail(summary, readRanges(row.parameterSpaceJson), readGrid(row.expandedParameterGridJson),
        Instant.ofEpochMilli(row.studyStartOpenTimeMs), Instant.ofEpochMilli(row.studyEndOpenTimeMs), row.trainingBars, row.validationBars,
        row.selectionMetric, row.minimumTrainTrades, row.orderAmount, row.feeRate, row.forceCloseAtEnd);
  }

  public ResearchStudyDtos.Detail parameterSpace(String id) { return get(id); }

  List<ResearchStudyDtos.Summary> summaries(List<ResearchStudyRow> rows) {
    if (rows == null || rows.isEmpty()) return List.of();
    List<WalkForwardStudyRow> children = walkForward.findByStudyIds(rows.stream().map(row -> row.walkForwardStudyId).toList());
    Map<String, com.aiprovider.controller.quant.dto.WalkForwardStudyDtos.StudySummary> childSummaries = walkForwardService.batchSummary(children);
    return rows.stream().map(row -> summary(row, childSummaries.get(row.walkForwardStudyId))).toList();
  }

  private ResearchStudyDtos.Summary summary(ResearchStudyRow row, com.aiprovider.controller.quant.dto.WalkForwardStudyDtos.StudySummary child) {
    if (child == null) throw error("RESEARCH_RESULT_INVALID", "walk-forward child is missing");
    return new ResearchStudyDtos.Summary(row.researchStudyId, row.name, row.description, row.datasetId, row.provider, row.marketType, row.dataType, row.symbol, row.intervalCode,
        row.strategyCode, row.strategyVersion, row.executionProfileCode, row.directionMode, row.orderSizingMode, row.evaluationMode, row.parameterSpaceMode, row.candidateCount,
        row.comparisonGroupKey, row.walkForwardStudyId, row.status, row.progressPercent, child.successfulOosFolds() == null ? 0 : child.successfulOosFolds(),
        child.failedFolds(), child.hasOosGaps(), child.totalOosReturnRatio(), child.oosMaximumDrawdownRatio(), child.totalOosTradeCount(), child.totalOosFees(), child.selectedParameterChanges(),
        row.errorCode, row.errorMessage, row.createdAt, row.startedAt, row.finishedAt, row.updatedAt);
  }

  private ResearchStudyRow require(String id) { if (id == null || id.isBlank()) throw error("RESEARCH_NOT_FOUND", "researchStudyId is required"); ResearchStudyRow row = research.findByResearchStudyId(id); if (row == null) throw error("RESEARCH_NOT_FOUND", "research study not found"); return row; }
  private Map<String, ResearchStudyDtos.IntegerRange> readRanges(String value) { try { return json.readValue(value, new TypeReference<LinkedHashMap<String, ResearchStudyDtos.IntegerRange>>() {}); } catch (Exception e) { throw error("RESEARCH_RESULT_INVALID", "parameter space JSON is invalid"); } }
  private Map<String, List<Integer>> readGrid(String value) { try { return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {}); } catch (Exception e) { throw error("RESEARCH_RESULT_INVALID", "parameter grid JSON is invalid"); } }
  private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase(Locale.ROOT); }
  private void validatePage(int page, int pageSize) { if (page < 1 || pageSize < 1 || pageSize > 100) throw error("RESEARCH_REQUEST_INVALID", "page/pageSize invalid"); }
  private ResearchStudyTaskException error(String code, String message) { return new ResearchStudyTaskException(code, message); }
}
