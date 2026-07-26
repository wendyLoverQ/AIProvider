package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import com.aiprovider.mapper.row.WalkForwardFoldRow;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WalkForwardFoldGenerator {
  private WalkForwardFoldGenerator() {}

  static Result generate(
      WalkForwardStudyCreateRequest request,
      MarketDataset dataset,
      QuantStrategyDefinition definition,
      int maxFolds,
      int maxChildRuns) {
    if (dataset == null
        || dataset.getInterval() == null
        || !dataset.getInterval().isFixedDuration())
      fail("WALK_FORWARD_WINDOW_INVALID", "dataset interval is not fixed");
    Instant start = request.getStudyStartOpenTimeInclusive();
    Instant end = request.getStudyEndOpenTimeExclusive();
    long interval = dataset.getInterval().durationMillis();
    if (start == null
        || end == null
        || !start.isBefore(end)
        || !dataset.getInterval().alignOpenTime(start).equals(start)
        || !dataset.getInterval().alignOpenTime(end).equals(end))
      fail("WALK_FORWARD_WINDOW_INVALID", "study window is not aligned or ordered");
    long totalBars = (end.toEpochMilli() - start.toEpochMilli()) / interval;
    if (totalBars < request.getTrainingBars() + (long) request.getValidationBars()
        || (totalBars - request.getTrainingBars()) % request.getValidationBars() != 0)
      fail("WALK_FORWARD_WINDOW_INVALID", "window tail is not an exact rolling validation window");
    long foldCountLong = (totalBars - request.getTrainingBars()) / request.getValidationBars();
    if (foldCountLong < 1 || foldCountLong > maxFolds)
      fail("WALK_FORWARD_TOO_LARGE", "fold count exceeds capacity");
    long candidateCountLong = 1;
    try {
      for (List<Integer> values : request.getParameterGrid().values())
        candidateCountLong = Math.multiplyExact(candidateCountLong, values.size());
    } catch (ArithmeticException e) {
      fail("WALK_FORWARD_TOO_LARGE", "candidate count overflow");
      return null;
    }
    if (candidateCountLong > 64) fail("WALK_FORWARD_TOO_LARGE", "candidate count exceeds capacity");
    int candidateCount = (int) candidateCountLong;
    long totalChildren;
    try {
      totalChildren = Math.multiplyExact(Math.multiplyExact(foldCountLong, candidateCount), 2L);
    } catch (ArithmeticException e) {
      fail("WALK_FORWARD_TOO_LARGE", "child run count overflow");
      return null;
    }
    if (totalChildren > maxChildRuns)
      fail("WALK_FORWARD_TOO_LARGE", "child run count exceeds capacity");
    List<Map<String, Integer>> combinations =
        BacktestExperimentGrid.expand(request.getParameterGrid(), definition, 64).combinations();
    int minimum =
        combinations.stream()
            .mapToInt(definition::minimumRequiredBars)
            .max()
            .orElse(Integer.MAX_VALUE);
    if (request.getTrainingBars() < minimum || request.getValidationBars() < minimum)
      fail("WALK_FORWARD_WINDOW_INVALID", "training or validation bars are below strategy minimum");
    List<WalkForwardFoldRow> folds = new ArrayList<>();
    for (int i = 0; i < foldCountLong; i++) {
      long trainStartMs = start.toEpochMilli() + i * (long) request.getValidationBars() * interval;
      long trainEndMs = trainStartMs + request.getTrainingBars() * interval;
      long validEndMs = trainEndMs + request.getValidationBars() * interval;
      WalkForwardFoldRow row = new WalkForwardFoldRow();
      row.foldId = UUID.randomUUID().toString();
      row.studyId = null;
      row.foldIndex = i;
      row.trainingStartOpenTimeMs = trainStartMs;
      row.trainingEndOpenTimeMs = trainEndMs;
      row.validationStartOpenTimeMs = trainEndMs;
      row.validationEndOpenTimeMs = validEndMs;
      row.experimentId = UUID.randomUUID().toString();
      row.status = WalkForwardFoldStatus.PENDING.name();
      folds.add(row);
    }
    if (!folds.isEmpty()
        && folds.get(folds.size() - 1).validationEndOpenTimeMs != end.toEpochMilli())
      fail("WALK_FORWARD_WINDOW_INVALID", "last validation window does not reach study end");
    return new Result(folds, combinations.size(), (int) totalChildren);
  }

  private static void fail(String code, String message) {
    throw new WalkForwardTaskException(code, message);
  }

  record Result(List<WalkForwardFoldRow> folds, int candidateCountPerFold, int totalChildRuns) {}
}
