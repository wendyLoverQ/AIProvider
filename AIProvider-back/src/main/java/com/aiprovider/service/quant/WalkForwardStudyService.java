package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardStudyService {
  private static final MathContext MC = MathContext.DECIMAL128;
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final BacktestRunMapper runs;
  private final BacktestEquityMapper equity;
  private final BacktestExperimentService experiments;
  private final ObjectMapper json;

  public WalkForwardStudyService(
      WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds,
      BacktestRunMapper runs,
      BacktestEquityMapper equity,
      BacktestExperimentService experiments,
      ObjectMapper json) {
    this.studies = studies;
    this.folds = folds;
    this.runs = runs;
    this.equity = equity;
    this.experiments = experiments;
    this.json = json;
  }

  public BacktestDtos.Page<WalkForwardStudyDtos.StudySummary> page(
      int page, int pageSize, String status, String symbol, String strategyCode) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      fail("WALK_FORWARD_REQUEST_INVALID", "page/pageSize invalid");
    String s = clean(status, true), sym = clean(symbol, true), code = clean(strategyCode, false);
    if (s != null)
      try {
        WalkForwardStudyStatus.valueOf(s);
      } catch (IllegalArgumentException e) {
        fail("WALK_FORWARD_REQUEST_INVALID", "status is invalid");
      }
    long offset = ((long) page - 1) * pageSize;
    if (offset > 10_000_000L) fail("WALK_FORWARD_REQUEST_INVALID", "page offset exceeds limit");
    List<WalkForwardStudyRow> rows = studies.findPage(s, sym, code, pageSize, offset);
    return new BacktestDtos.Page<>(
        rows.stream().map(this::refreshSummary).toList(),
        studies.count(s, sym, code),
        page,
        pageSize);
  }

  public WalkForwardStudyDtos.StudyDetail get(String studyId) {
    WalkForwardStudyRow row = require(studyId);
    List<WalkForwardFoldRow> all = folds.findAllByStudyId(studyId);
    WalkForwardStudyDtos.StudySummary summary = refreshSummary(row);
    return new WalkForwardStudyDtos.StudyDetail(summary, frequencies(all));
  }

  public BacktestDtos.Page<WalkForwardStudyDtos.FoldResult> folds(
      String studyId, int page, int pageSize) {
    require(studyId);
    if (page < 1 || pageSize < 1 || pageSize > 100)
      fail("WALK_FORWARD_REQUEST_INVALID", "page/pageSize invalid");
    long offset = ((long) page - 1) * pageSize;
    return new BacktestDtos.Page<>(
        folds.findPage(studyId, pageSize, offset).stream().map(this::foldResult).toList(),
        folds.count(studyId),
        page,
        pageSize);
  }

  public WalkForwardStudyDtos.OosEquity oosEquity(String studyId, int limit) {
    if (limit < 100 || limit > 5000)
      fail("WALK_FORWARD_REQUEST_INVALID", "limit must be 100..5000");
    WalkForwardStudyRow study = require(studyId);
    WalkForwardStudyDtos.StudySummary summary = refreshSummary(study);
    if (!terminal(summary.status())) fail("WALK_FORWARD_NOT_TERMINAL", "study is not terminal");
    List<WalkForwardFoldRow> allFolds = folds.findAllByStudyId(studyId);
    List<WalkForwardFoldRow> successful =
        allFolds.stream()
            .filter(f -> "COMPLETED".equals(f.status))
            .sorted(Comparator.comparingInt(f -> f.foldIndex))
            .toList();
    List<WalkForwardStudyDtos.OosPoint> points = new ArrayList<>();
    BigDecimal previousEnd = BigDecimal.ONE, runningPeak = BigDecimal.ONE;
    Instant previousTime = null;
    for (WalkForwardFoldRow fold : successful) {
      List<BacktestEquityRow> raw = equity.findAll(fold.selectedValidationRunId);
      if (raw.isEmpty()) fail("WALK_FORWARD_OOS_INVALID", "selected validation equity is empty");
      BigDecimal first = raw.get(0).equityRatio;
      if (first == null || first.signum() <= 0)
        fail("WALK_FORWARD_OOS_INVALID", "first equity must be positive");
      for (BacktestEquityRow point : raw) {
        if (point.equityRatio == null || point.equityRatio.signum() <= 0)
          fail("WALK_FORWARD_OOS_INVALID", "equity ratio must be positive");
        Instant time = Instant.ofEpochMilli(point.openTimeMs);
        if (previousTime != null && !time.isAfter(previousTime))
          fail("WALK_FORWARD_OOS_INVALID", "equity time is not strictly increasing");
        BigDecimal normalized = previousEnd.multiply(point.equityRatio.divide(first, MC), MC);
        runningPeak = runningPeak.max(normalized);
        BigDecimal drawdown =
            runningPeak.signum() == 0
                ? BigDecimal.ZERO
                : runningPeak.subtract(normalized, MC).divide(runningPeak, MC);
        points.add(
            new WalkForwardStudyDtos.OosPoint(
                points.size(), fold.foldIndex, time, normalized, drawdown));
        previousTime = time;
      }
      previousEnd = points.get(points.size() - 1).indexRatio();
    }
    int total = points.size();
    List<WalkForwardStudyDtos.OosPoint> sampled =
        BacktestEquitySampler.indices(total, limit).stream().map(points::get).toList();
    BigDecimal maxDrawdown =
        points.stream()
            .map(WalkForwardStudyDtos.OosPoint::drawdownRatio)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
    return new WalkForwardStudyDtos.OosEquity(
        total > sampled.size(),
        total,
        successful.size(),
        allFolds.size() - successful.size(),
        successful.size() != allFolds.size(),
        summary.totalOosReturnRatio(),
        maxDrawdown,
        sampled);
  }

  private WalkForwardStudyDtos.StudySummary refreshSummary(WalkForwardStudyRow row) {
    List<WalkForwardFoldRow> all = folds.findAllByStudyId(row.studyId);
    Aggregate aggregate = aggregate(all);
    if (!Objects.equals(row.status, aggregate.status)
        || row.progressPercent == null
        || row.progressPercent.compareTo(aggregate.progress) != 0) {
      Instant now = Instant.now();
      Instant finished =
          terminal(aggregate.status) ? (row.finishedAt == null ? now : row.finishedAt) : null;
      if (studies.updateAggregate(
              row.studyId,
              aggregate.status,
              aggregate.progress,
              aggregate.errorCode,
              aggregate.errorMessage,
              finished,
              now)
          != 1) fail("WALK_FORWARD_STATE_CONFLICT", "study aggregate update failed");
      row = require(row.studyId);
    }
    OosSummary oos = terminal(row.status) ? oosSummary(all) : OosSummary.empty();
    return summary(row, aggregate, oos);
  }

  private Aggregate aggregate(List<WalkForwardFoldRow> all) {
    int pending = 0, active = 0, completed = 0, failed = 0;
    BigDecimal progress = BigDecimal.ZERO;
    for (WalkForwardFoldRow fold : all) {
      BigDecimal foldProgress = BigDecimal.ZERO;
      if ("WAITING_EXPERIMENT".equals(fold.status)) {
        active++;
        try {
          foldProgress = experiments.get(fold.experimentId).progressPercent();
        } catch (RuntimeException ignored) {
          foldProgress = BigDecimal.ZERO;
        }
      } else if ("CREATING_EXPERIMENT".equals(fold.status)) active++;
      else if ("PENDING".equals(fold.status)) pending++;
      else if ("COMPLETED".equals(fold.status)) {
        completed++;
        foldProgress = BigDecimal.valueOf(100);
      } else if ("FAILED".equals(fold.status)) {
        failed++;
        foldProgress = BigDecimal.valueOf(100);
      }
      progress = progress.add(foldProgress, MC);
    }
    BigDecimal average =
        all.isEmpty()
            ? BigDecimal.ZERO
            : progress.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_UP);
    String status =
        pending + active == all.size() && completed == 0 && failed == 0
            ? "QUEUED"
            : pending + active > 0
                ? "RUNNING"
                : failed == 0 ? "COMPLETED" : completed > 0 ? "COMPLETED_WITH_FAILURES" : "FAILED";
    return new Aggregate(
        status,
        average,
        pending,
        active,
        completed,
        failed,
        failed > 0 ? "WALK_FORWARD_SELECTED_VALIDATION_FAILED" : null,
        failed > 0 ? "one or more folds failed" : null);
  }

  private WalkForwardStudyDtos.StudySummary summary(
      WalkForwardStudyRow row, Aggregate a, OosSummary oos) {
    return new WalkForwardStudyDtos.StudySummary(
        row.studyId,
        row.datasetId,
        row.provider,
        row.marketType,
        row.dataType,
        row.symbol,
        row.intervalCode,
        row.strategyCode,
        row.strategyVersion,
        readGrid(row.parameterGridJson),
        row.windowMode,
        Instant.ofEpochMilli(row.studyStartOpenTimeMs),
        Instant.ofEpochMilli(row.studyEndOpenTimeMs),
        row.trainingBars,
        row.validationBars,
        row.stepBars,
        row.foldCount,
        row.candidateCountPerFold,
        row.totalChildRuns,
        row.selectionMetric,
        row.minimumTrainTrades,
        row.orderAmount,
        row.feeRate,
        row.forceCloseAtEnd,
        row.status,
        row.progressPercent == null ? a.progress : row.progressPercent,
        a.pending,
        a.active,
        a.completed,
        a.failed,
        oos.parameterChanges,
        oos.successfulFolds,
        terminal(row.status) ? oos.hasGaps : null,
        oos.tradeCount,
        oos.fees,
        oos.returnRatio,
        row.errorCode,
        row.errorMessage,
        row.createdAt,
        row.startedAt,
        row.finishedAt,
        row.updatedAt);
  }

  private WalkForwardStudyDtos.FoldResult foldResult(WalkForwardFoldRow fold) {
    BacktestExperimentDtos.ExperimentSummary experiment = null;
    if ("WAITING_EXPERIMENT".equals(fold.status) || "COMPLETED".equals(fold.status))
      try {
        experiment = experiments.get(fold.experimentId);
      } catch (RuntimeException ignored) {
      }
    BacktestRunRow training =
        fold.selectedTrainingRunId == null ? null : runs.findByRunId(fold.selectedTrainingRunId);
    BacktestRunRow validation =
        fold.selectedValidationRunId == null
            ? null
            : runs.findByRunId(fold.selectedValidationRunId);
    return new WalkForwardStudyDtos.FoldResult(
        fold.foldId,
        fold.foldIndex,
        Instant.ofEpochMilli(fold.trainingStartOpenTimeMs),
        Instant.ofEpochMilli(fold.trainingEndOpenTimeMs),
        Instant.ofEpochMilli(fold.validationStartOpenTimeMs),
        Instant.ofEpochMilli(fold.validationEndOpenTimeMs),
        fold.experimentId,
        fold.status,
        "WAITING_EXPERIMENT".equals(fold.status) && experiment != null
            ? experiment.progressPercent()
            : ("COMPLETED".equals(fold.status) || "FAILED".equals(fold.status)
                ? BigDecimal.valueOf(100)
                : BigDecimal.ZERO),
        experiment == null ? null : experiment.status(),
        fold.selectedCandidateId,
        readParameters(fold.selectedParametersJson),
        fold.selectedTrainingRunId,
        fold.selectedValidationRunId,
        fold.selectionMetricValue,
        metrics(training),
        metrics(validation),
        fold.errorCode,
        fold.errorMessage,
        fold.startedAt,
        fold.finishedAt,
        fold.updatedAt);
  }

  private List<WalkForwardStudyDtos.ParameterSelectionFrequency> frequencies(
      List<WalkForwardFoldRow> rows) {
    Map<String, List<WalkForwardFoldRow>> grouped = new LinkedHashMap<>();
    for (WalkForwardFoldRow row : rows)
      if (row.selectedParametersJson != null)
        grouped.computeIfAbsent(row.selectedParametersJson, k -> new ArrayList<>()).add(row);
    return grouped.values().stream()
        .map(
            group ->
                new WalkForwardStudyDtos.ParameterSelectionFrequency(
                    readParameters(group.get(0).selectedParametersJson),
                    group.size(),
                    group.stream().mapToInt(f -> f.foldIndex).min().orElse(0),
                    group.stream().mapToInt(f -> f.foldIndex).max().orElse(0)))
        .toList();
  }

  private OosSummary oosSummary(List<WalkForwardFoldRow> all) {
    List<WalkForwardFoldRow> selected =
        all.stream()
            .filter(f -> "COMPLETED".equals(f.status))
            .sorted(Comparator.comparingInt(f -> f.foldIndex))
            .toList();
    BigDecimal compound = BigDecimal.ONE, fees = BigDecimal.ZERO;
    int trades = 0, changes = 0;
    Map<String, Integer> previous = null;
    for (WalkForwardFoldRow fold : selected) {
      BacktestRunRow run = runs.findByRunId(fold.selectedValidationRunId);
      if (run == null
          || run.totalReturnRatio == null
          || run.totalFees == null
          || run.tradeCount == null) return OosSummary.empty();
      compound = compound.multiply(BigDecimal.ONE.add(run.totalReturnRatio, MC), MC);
      fees = fees.add(run.totalFees, MC);
      trades += run.tradeCount;
      Map<String, Integer> current = readParameters(fold.selectedParametersJson);
      if (previous != null && !previous.equals(current)) changes++;
      previous = current;
    }
    return new OosSummary(
        selected.size(),
        all.size() != selected.size(),
        trades,
        fees,
        compound.subtract(BigDecimal.ONE, MC),
        changes);
  }

  private BacktestDtos.Metrics metrics(BacktestRunRow r) {
    return r == null
        ? null
        : new BacktestDtos.Metrics(
            r.tradeCount,
            r.winningTradeCount,
            r.losingTradeCount,
            r.breakEvenTradeCount,
            r.winRate,
            r.grossProfit,
            r.grossLoss,
            r.netProfit,
            r.totalReturnRatio,
            r.maximumDrawdownRatio,
            r.profitFactor,
            r.averageTradeReturnRatio,
            r.buyAndHoldReturnRatio,
            r.totalFees);
  }

  private Map<String, List<Integer>> readGrid(String value) {
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {});
    } catch (Exception e) {
      fail("WALK_FORWARD_OOS_INVALID", "stored grid JSON is invalid");
      return Map.of();
    }
  }

  private Map<String, Integer> readParameters(String value) {
    if (value == null) return null;
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, Integer>>() {});
    } catch (Exception e) {
      fail("WALK_FORWARD_OOS_INVALID", "stored parameters JSON is invalid");
      return Map.of();
    }
  }

  private WalkForwardStudyRow require(String id) {
    WalkForwardStudyRow row = studies.findByStudyId(id);
    if (row == null) fail("WALK_FORWARD_NOT_FOUND", "studyId not found");
    return row;
  }

  private boolean terminal(String s) {
    return Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(s);
  }

  private String clean(String value, boolean upper) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim();
    return upper ? v.toUpperCase(Locale.ROOT) : v;
  }

  private void fail(String code, String message) {
    throw new WalkForwardTaskException(code, message);
  }

  private record Aggregate(
      String status,
      BigDecimal progress,
      int pending,
      int active,
      int completed,
      int failed,
      String errorCode,
      String errorMessage) {}

  private record OosSummary(
      int successfulFolds,
      boolean hasGaps,
      int tradeCount,
      BigDecimal fees,
      BigDecimal returnRatio,
      int parameterChanges) {
    static OosSummary empty() {
      return new OosSummary(0, false, 0, null, null, 0);
    }
  }
}
