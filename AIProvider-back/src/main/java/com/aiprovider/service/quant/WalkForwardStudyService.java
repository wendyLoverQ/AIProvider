package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardStudyService {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final BigDecimal NON_TERMINAL_MAX_PROGRESS = new BigDecimal("99.99");
  private static final MathContext MC = MathContext.DECIMAL128;
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final WalkForwardStudySnapshotLoader snapshots;
  private final ObjectMapper json;
  private final WalkForwardOosCalculator oosCalculator;

  @org.springframework.beans.factory.annotation.Autowired
  public WalkForwardStudyService(
      WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds,
      WalkForwardStudySnapshotLoader snapshots,
      ObjectMapper json,
      WalkForwardOosCalculator oosCalculator) {
    this.studies = studies;
    this.folds = folds;
    this.snapshots = snapshots;
    this.json = json;
    this.oosCalculator = oosCalculator;
  }

  public WalkForwardStudyService(WalkForwardStudyMapper studies, WalkForwardFoldMapper folds,
      WalkForwardStudySnapshotLoader snapshots, ObjectMapper json) {
    this(studies, folds, snapshots, json, new WalkForwardOosCalculator(json));
  }

  public BacktestDtos.Page<WalkForwardStudyDtos.StudySummary> page(
      int page, int pageSize, String status, String symbol, String strategyCode) {
    validatePage(page, pageSize);
    String s = clean(status, true), sym = clean(symbol, true), code = clean(strategyCode, false);
    if (s != null)
      try {
        WalkForwardStudyStatus.valueOf(s);
      } catch (IllegalArgumentException e) {
        fail("WALK_FORWARD_REQUEST_INVALID", "status is invalid");
      }
    long offset = offset(page, pageSize);
    List<WalkForwardStudyRow> rows = studies.findPage(s, sym, code, pageSize, offset);
    Map<String, WalkForwardStudySnapshot> loaded = snapshots.loadMany(rows, true);
    List<WalkForwardStudyDtos.StudySummary> result =
        rows.stream().map(row -> refreshSummary(requiredSnapshot(loaded, row.studyId))).toList();
    return new BacktestDtos.Page<>(result, studies.count(s, sym, code), page, pageSize);
  }

  public WalkForwardStudyDtos.StudyDetail get(String studyId) {
    WalkForwardStudyRow row = require(studyId);
    WalkForwardStudySnapshot snapshot =
        snapshots.load(row, folds.findAllByStudyId(studyId), true);
    return new WalkForwardStudyDtos.StudyDetail(
        refreshSummary(snapshot), frequencies(snapshot.folds()));
  }

  public BacktestDtos.Page<WalkForwardStudyDtos.FoldResult> folds(
      String studyId, int page, int pageSize) {
    WalkForwardStudyRow study = require(studyId);
    validatePage(page, pageSize);
    long offset = offset(page, pageSize);
    List<WalkForwardFoldRow> pageRows = folds.findPage(studyId, pageSize, offset);
    WalkForwardStudySnapshot snapshot = snapshots.load(study, pageRows, false);
    return new BacktestDtos.Page<>(
        pageRows.stream().map(fold -> foldResult(fold, snapshot)).toList(),
        folds.count(studyId),
        page,
        pageSize);
  }

  public WalkForwardStudyDtos.OosEquity oosEquity(String studyId, int limit) {
    if (limit < 100 || limit > 5000)
      fail("WALK_FORWARD_REQUEST_INVALID", "limit must be 100..5000");
    WalkForwardStudyRow study = require(studyId);
    WalkForwardStudySnapshot snapshot = snapshots.load(study, folds.findAllByStudyId(studyId), true);
    if (!terminal(study.status)) fail("WALK_FORWARD_NOT_TERMINAL", "study is not terminal");
    WalkForwardOosCalculation calculation = oosCalculator.calculate(study, snapshot.folds(), snapshot.runs(), snapshot.equities());
    List<WalkForwardStudyDtos.OosPoint> points = calculation.points();
    List<WalkForwardStudyDtos.OosPoint> sampled = sample(points, limit);
    return new WalkForwardStudyDtos.OosEquity(
        points.size() > sampled.size(),
        points.size(),
        calculation.successfulFolds(),
        calculation.failedFolds(),
        calculation.hasGaps(),
        calculation.totalReturnRatio(),
        calculation.maximumDrawdownRatio(),
        sampled);
  }

  WalkForwardStudyDtos.StudySummary refreshAggregate(WalkForwardStudySnapshot snapshot) {
    return refreshSummary(snapshot);
  }

  /** Batch result view used by Research Study; it never performs one child GET per study. */
  public Map<String, WalkForwardStudyDtos.StudySummary> batchSummary(List<WalkForwardStudyRow> rows) {
    if (rows == null || rows.isEmpty()) return Map.of();
    Map<String, WalkForwardStudySnapshot> loaded = snapshots.loadMany(rows, true);
    Map<String, WalkForwardStudyDtos.StudySummary> result = new LinkedHashMap<>();
    for (WalkForwardStudyRow row : rows) {
      WalkForwardStudySnapshot snapshot = loaded.get(row.studyId);
      if (snapshot == null) fail("WALK_FORWARD_STATE_CONFLICT", "study snapshot missing");
      result.put(row.studyId, refreshSummary(snapshot));
    }
    return Map.copyOf(result);
  }

  private WalkForwardStudyDtos.StudySummary refreshSummary(WalkForwardStudySnapshot snapshot) {
    Aggregate aggregate = aggregate(snapshot);
    WalkForwardStudyRow row = snapshot.study();
    if (terminal(aggregate.status) && snapshot.equities().isEmpty()) {
      snapshot = snapshots.load(row, snapshot.folds(), true);
    }
    OosSummary targetOos = terminal(aggregate.status) ? oosSummary(snapshot, aggregate.status) : OosSummary.empty();
    if (!Objects.equals(row.status, aggregate.status)
        || compare(row.progressPercent, aggregate.progress) != 0
        || !Objects.equals(row.errorCode, aggregate.errorCode)
        || !Objects.equals(row.errorMessage, aggregate.errorMessage)
        || ("RUNNING".equals(aggregate.status) && row.startedAt == null)
        || (!terminal(aggregate.status) && row.finishedAt != null)
        || (terminal(aggregate.status) && row.finishedAt == null)
        || !oosMatches(row, targetOos, aggregate.status)) {
      Instant now = Instant.now();
      Instant finished = terminal(aggregate.status) ? now : null;
      int affected;
      if (terminal(aggregate.status)) {
        affected = studies.updateAggregateWithOos(row.studyId, row.status, aggregate.status, aggregate.progress,
            aggregate.errorCode, aggregate.errorMessage, finished, targetOos.successfulFolds, targetOos.failedFolds,
            targetOos.hasGaps, targetOos.returnRatio, targetOos.maximumDrawdown, targetOos.tradeCount,
            targetOos.fees, targetOos.parameterChanges, (short) 1, now);
      } else {
        affected = studies.updateAggregate(row.studyId, row.status, aggregate.status, aggregate.progress,
            aggregate.errorCode, aggregate.errorMessage, finished, now);
      }
      if (affected > 1) fail("WALK_FORWARD_STATE_CONFLICT", "study aggregate affected multiple rows");
      if (affected == 0) {
        WalkForwardStudyRow latest = require(row.studyId);
        WalkForwardStudySnapshot current =
            snapshots.load(latest, folds.findAllByStudyId(row.studyId), true);
        Aggregate currentAggregate = aggregate(current);
        OosSummary currentOos = terminal(latest.status) ? oosSummary(current, latest.status) : OosSummary.empty();
        return summary(latest, currentAggregate, currentOos);
      }
      row = require(row.studyId);
      snapshot = new WalkForwardStudySnapshot(row, snapshot.folds(), snapshot.experiments(), snapshot.runs(), snapshot.equities());
    }
    OosSummary oos = terminal(row.status) ? oosSummary(snapshot, row.status) : OosSummary.empty();
    return summary(row, aggregate, oos);
  }

  private Aggregate aggregate(WalkForwardStudySnapshot snapshot) {
    List<WalkForwardFoldRow> all = snapshot.folds();
    if (all.size() != snapshot.study().foldCount)
      fail("WALK_FORWARD_STATE_CONFLICT", "fold count does not match study");
    int pending = 0, active = 0, completed = 0, failed = 0;
    BigDecimal progressSum = BigDecimal.ZERO;
    WalkForwardFoldRow firstFailure = null;
    for (WalkForwardFoldRow fold : all) {
      if (fold.status == null) fail("WALK_FORWARD_STATE_CONFLICT", "fold status is null");
      BigDecimal foldProgress = BigDecimal.ZERO;
      switch (fold.status) {
        case "PENDING" -> { pending++; foldProgress = BigDecimal.ZERO; }
        case "CREATING_EXPERIMENT" -> { active++; foldProgress = BigDecimal.ZERO; }
        case "WAITING_EXPERIMENT" -> {
          active++;
          var experiment = snapshot.experiment(fold.experimentId);
          if (experiment == null)
            fail("WALK_FORWARD_EXPERIMENT_NOT_FOUND", "experimentId=" + fold.experimentId);
          if (experiment.progressPercent() == null)
            fail("WALK_FORWARD_STATE_CONFLICT", "experiment progress is null");
          foldProgress = clamp(experiment.progressPercent());
        }
        case "COMPLETED" -> { completed++; foldProgress = HUNDRED; }
        case "FAILED" -> {
          failed++; foldProgress = HUNDRED;
          if (firstFailure == null || fold.foldIndex < firstFailure.foldIndex) firstFailure = fold;
        }
        default -> fail("WALK_FORWARD_STATE_CONFLICT", "unknown fold status=" + fold.status);
      }
      progressSum = progressSum.add(foldProgress, MC);
    }
    if (pending + active + completed + failed != all.size())
      fail("WALK_FORWARD_STATE_CONFLICT", "fold state count is inconsistent");
    String status = null;
    if (pending == all.size() && active == 0 && completed == 0 && failed == 0) status = "QUEUED";
    else if (completed == all.size() && failed == 0 && pending == 0 && active == 0) status = "COMPLETED";
    else if (completed > 0 && failed > 0 && pending == 0 && active == 0) status = "COMPLETED_WITH_FAILURES";
    else if (failed == all.size() && completed == 0 && pending == 0 && active == 0) status = "FAILED";
    else if (pending + active > 0) status = "RUNNING";
    else fail("WALK_FORWARD_STATE_CONFLICT", "fold state has no valid aggregate");
    BigDecimal progress =
        terminal(status)
            ? HUNDRED
            : all.isEmpty()
                ? BigDecimal.ZERO
                : progressSum.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_UP);
    progress = clamp(progress);
    if (!terminal(status) && progress.compareTo(HUNDRED) >= 0) progress = NON_TERMINAL_MAX_PROGRESS;
    String errorCode = null, errorMessage = null;
    if (firstFailure != null && ("COMPLETED_WITH_FAILURES".equals(status) || "FAILED".equals(status))) {
      if (firstFailure.errorCode == null)
        fail("WALK_FORWARD_STATE_CONFLICT", "failed fold has no error code");
      errorCode = firstFailure.errorCode;
      errorMessage = cleanMessage(firstFailure.errorMessage) + " failedFolds=" + failed;
    }
    return new Aggregate(status, progress, pending, active, completed, failed, errorCode, errorMessage);
  }

  private WalkForwardStudyDtos.StudySummary summary(
      WalkForwardStudyRow row, Aggregate aggregate, OosSummary oos) {
    return new WalkForwardStudyDtos.StudySummary(
        row.studyId, row.datasetId, row.provider, row.marketType, row.dataType, row.symbol,
        row.intervalCode, row.strategyCode, row.strategyVersion, row.executionProfileCode,
        row.directionMode, row.orderSizingMode, readGrid(row.parameterGridJson),
        row.windowMode, Instant.ofEpochMilli(row.studyStartOpenTimeMs), Instant.ofEpochMilli(row.studyEndOpenTimeMs),
        row.trainingBars, row.validationBars, row.stepBars, row.foldCount, row.candidateCountPerFold,
        row.totalChildRuns, row.selectionMetric, row.minimumTrainTrades, row.orderAmount, row.feeRate,
        row.forceCloseAtEnd, row.status, row.progressPercent, aggregate.pending, aggregate.active,
        aggregate.completed, aggregate.failed, oos.parameterChanges, oos.successfulFolds,
        terminal(row.status) ? oos.hasGaps : null, oos.tradeCount, oos.fees, oos.returnRatio, oos.maximumDrawdown,
        row.errorCode, row.errorMessage, row.createdAt, row.startedAt, row.finishedAt, row.updatedAt);
  }

  private WalkForwardStudyDtos.FoldResult foldResult(
      WalkForwardFoldRow fold, WalkForwardStudySnapshot snapshot) {
    var experiment = snapshot.experiment(fold.experimentId);
    if (("WAITING_EXPERIMENT".equals(fold.status) || "COMPLETED".equals(fold.status))
        && experiment == null)
      fail("WALK_FORWARD_EXPERIMENT_NOT_FOUND", "experimentId=" + fold.experimentId);
    if (experiment != null && experiment.progressPercent() == null)
      fail("WALK_FORWARD_STATE_CONFLICT", "experiment progress is null");
    BacktestRunRow training = snapshot.run(fold.selectedTrainingRunId), validation = snapshot.run(fold.selectedValidationRunId);
    return new WalkForwardStudyDtos.FoldResult(
        fold.foldId, fold.foldIndex, Instant.ofEpochMilli(fold.trainingStartOpenTimeMs), Instant.ofEpochMilli(fold.trainingEndOpenTimeMs),
        Instant.ofEpochMilli(fold.validationStartOpenTimeMs), Instant.ofEpochMilli(fold.validationEndOpenTimeMs), fold.experimentId,
        fold.status, experiment == null ? ("COMPLETED".equals(fold.status) || "FAILED".equals(fold.status) ? HUNDRED : BigDecimal.ZERO) : clamp(experiment.progressPercent()),
        experiment == null ? null : experiment.status(), fold.selectedCandidateId, readParameters(fold.selectedParametersJson),
        fold.selectedTrainingRunId, fold.selectedValidationRunId, fold.selectionMetricValue, metrics(training), metrics(validation),
        fold.errorCode, fold.errorMessage, fold.startedAt, fold.finishedAt, fold.updatedAt);
  }

  private List<WalkForwardStudyDtos.ParameterSelectionFrequency> frequencies(List<WalkForwardFoldRow> rows) {
    Map<String, List<WalkForwardFoldRow>> grouped = new LinkedHashMap<>();
    for (WalkForwardFoldRow row : rows)
      if (row.selectedParametersJson != null)
        grouped.computeIfAbsent(row.selectedParametersJson, ignored -> new ArrayList<>()).add(row);
    return grouped.values().stream().map(group -> new WalkForwardStudyDtos.ParameterSelectionFrequency(
        readParameters(group.get(0).selectedParametersJson), group.size(),
        group.stream().mapToInt(f -> f.foldIndex).min().orElse(0), group.stream().mapToInt(f -> f.foldIndex).max().orElse(0))).toList();
  }

  private OosSummary oosSummary(WalkForwardStudySnapshot snapshot, String status) {
    WalkForwardOosCalculation calculation = oosCalculator.calculateForTerminalStatus(snapshot.study(), status,
        snapshot.folds(), snapshot.runs(), snapshot.equities());
    return new OosSummary(calculation.successfulFolds(), calculation.failedFolds(), calculation.hasGaps(), calculation.tradeCount(),
        calculation.totalFees(), calculation.totalReturnRatio(), calculation.maximumDrawdownRatio(), calculation.parameterChanges());
  }

  private boolean oosMatches(WalkForwardStudyRow row, OosSummary expected, String status) {
    if (!terminal(status)) {
      return row.successfulOosFolds == null && row.failedFolds == null && row.hasOosGaps == null
          && row.oosTotalReturnRatio == null && row.oosMaximumDrawdownRatio == null && row.oosTradeCount == null
          && row.oosTotalFees == null && row.parameterChanges == null && row.oosAggregateVersion == null;
    }
    return Objects.equals(row.successfulOosFolds, expected.successfulFolds)
        && Objects.equals(row.failedFolds, expected.failedFolds)
        && Objects.equals(row.hasOosGaps, expected.hasGaps)
        && WalkForwardOosNumbers.numericallyEqual(row.oosTotalReturnRatio, expected.returnRatio)
        && WalkForwardOosNumbers.numericallyEqual(row.oosMaximumDrawdownRatio, expected.maximumDrawdown)
        && Objects.equals(row.oosTradeCount, expected.tradeCount)
        && WalkForwardOosNumbers.numericallyEqual(row.oosTotalFees, expected.fees)
        && Objects.equals(row.parameterChanges, expected.parameterChanges)
        && Objects.equals(row.oosAggregateVersion, (short) 1);
  }

  private List<WalkForwardFoldRow> successfulFolds(List<WalkForwardFoldRow> all) {
    return all.stream().filter(f -> "COMPLETED".equals(f.status)).sorted(Comparator.comparingInt(f -> f.foldIndex)).toList();
  }

  private List<WalkForwardStudyDtos.OosPoint> sample(List<WalkForwardStudyDtos.OosPoint> points, int limit) {
    if (points.size() <= limit) return points;
    LinkedHashSet<Integer> indexes = new LinkedHashSet<>(BacktestEquitySampler.indices(points.size(), limit));
    int maximum = 0;
    for (int i = 1; i < points.size(); i++)
      if (points.get(i).drawdownRatio().compareTo(points.get(maximum).drawdownRatio()) > 0) maximum = i;
    indexes.add(0); indexes.add(points.size() - 1); indexes.add(maximum);
    if (indexes.size() > limit) {
      Iterator<Integer> iterator = indexes.iterator();
      while (indexes.size() > limit && iterator.hasNext()) {
        int index = iterator.next();
        if (index != 0 && index != points.size() - 1 && index != maximum) iterator.remove();
      }
    }
    return indexes.stream().sorted().map(points::get).toList();
  }

  private BacktestDtos.Metrics metrics(BacktestRunRow r) {
    return r == null ? null : new BacktestDtos.Metrics(r.tradeCount, r.winningTradeCount, r.losingTradeCount, r.breakEvenTradeCount, r.winRate, r.grossProfit, r.grossLoss, r.netProfit, r.totalReturnRatio, r.maximumDrawdownRatio, r.profitFactor, r.averageTradeReturnRatio, r.buyAndHoldReturnRatio, r.totalFees);
  }

  private WalkForwardStudySnapshot requiredSnapshot(Map<String, WalkForwardStudySnapshot> values, String id) {
    WalkForwardStudySnapshot value = values.get(id);
    if (value == null) fail("WALK_FORWARD_STATE_CONFLICT", "study snapshot missing");
    return value;
  }

  private WalkForwardStudyRow require(String id) {
    WalkForwardStudyRow row = studies.findByStudyId(id);
    if (row == null) fail("WALK_FORWARD_NOT_FOUND", "studyId not found");
    return row;
  }

  private Map<String, List<Integer>> readGrid(String value) {
    try { return json.readValue(value, new TypeReference<LinkedHashMap<String, List<Integer>>>() {}); }
    catch (Exception e) { fail("WALK_FORWARD_OOS_INVALID", "stored grid JSON is invalid"); return Map.of(); }
  }

  private Map<String, Integer> readParameters(String value) {
    if (value == null) return null;
    try { return json.readValue(value, new TypeReference<LinkedHashMap<String, Integer>>() {}); }
    catch (Exception e) { fail("WALK_FORWARD_OOS_INVALID", "stored parameters JSON is invalid"); return Map.of(); }
  }

  private BigDecimal clamp(BigDecimal value) { return value.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP); }
  private int compare(BigDecimal left, BigDecimal right) { return left == null ? (right == null ? 0 : -1) : left.compareTo(right); }
  private boolean terminal(String value) { return Set.of("COMPLETED", "COMPLETED_WITH_FAILURES", "FAILED").contains(value); }
  private String clean(String value, boolean upper) { if (value == null || value.isBlank()) return null; String result = value.trim(); return upper ? result.toUpperCase(Locale.ROOT) : result; }
  private String cleanMessage(String value) { String result = value == null || value.isBlank() ? "fold failed" : value.replaceAll("[\\r\\n]", " "); return result.substring(0, Math.min(1000, result.length())); }
  private void validatePage(int page, int pageSize) { if (page < 1 || pageSize < 1 || pageSize > 100) fail("WALK_FORWARD_REQUEST_INVALID", "page/pageSize invalid"); }
  private long offset(int page, int pageSize) { try { long value = Math.multiplyExact((long) page - 1, pageSize); if (value > 10_000_000L) fail("WALK_FORWARD_REQUEST_INVALID", "page offset exceeds limit"); return value; } catch (ArithmeticException e) { fail("WALK_FORWARD_REQUEST_INVALID", "page offset overflow"); return 0; } }
  private void fail(String code, String message) { throw new WalkForwardTaskException(code, message); }

  private record Aggregate(String status, BigDecimal progress, int pending, int active, int completed, int failed, String errorCode, String errorMessage) {}
  private record OosSummary(Integer successfulFolds, Integer failedFolds, Boolean hasGaps, Integer tradeCount,
                            BigDecimal fees, BigDecimal returnRatio, BigDecimal maximumDrawdown, Integer parameterChanges) {
    static OosSummary empty() { return new OosSummary(null, null, null, null, null, null, null, null); }
  }
}
