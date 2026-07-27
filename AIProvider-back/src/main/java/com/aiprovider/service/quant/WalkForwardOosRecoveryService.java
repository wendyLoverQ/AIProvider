package com.aiprovider.service.quant;

import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardOosRecoveryService {
  private static final Logger log = LogManager.getLogger(WalkForwardOosRecoveryService.class);
  private final WalkForwardStudyMapper studies;
  private final WalkForwardFoldMapper folds;
  private final WalkForwardStudySnapshotLoader snapshots;
  private final WalkForwardOosCalculator calculator;

  @Autowired
  public WalkForwardOosRecoveryService(WalkForwardStudyMapper studies, WalkForwardFoldMapper folds,
      WalkForwardStudySnapshotLoader snapshots, WalkForwardOosCalculator calculator) {
    this.studies = studies; this.folds = folds; this.snapshots = snapshots; this.calculator = calculator;
  }

  public WalkForwardOosRecoveryService(WalkForwardStudyMapper studies, WalkForwardStudySnapshotLoader snapshots,
      WalkForwardOosCalculator calculator) {
    this(studies, null, snapshots, calculator);
  }

  public void recoverBatch(int limit) {
    List<WalkForwardStudyRow> rows = studies.findTerminalMissingOosAggregate(limit);
    if (rows.isEmpty()) return;
    Map<String, WalkForwardStudySnapshot> loaded;
    try {
      loaded = snapshots.loadMany(rows, true);
    } catch (RuntimeException exception) {
      if (WalkForwardOosRecoveryErrorClassifier.classify(exception)
          == WalkForwardOosRecoveryErrorClassifier.Kind.RETRYABLE_BATCH_ERROR) {
        log.error("operation=walk-forward-oos-recovery result=retryable_batch", exception);
        return;
      }
      log.warn("operation=walk-forward-oos-recovery result=permanent_batch_fallback");
      recoverIndividually(rows);
      return;
    }
    recoverLoaded(rows, loaded);
  }

  private void recoverLoaded(List<WalkForwardStudyRow> rows, Map<String, WalkForwardStudySnapshot> loaded) {
    for (WalkForwardStudyRow row : rows) {
      try {
        WalkForwardStudySnapshot snapshot = loaded.get(row.studyId);
        if (snapshot == null) throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "study snapshot missing");
        recoverOne(row, snapshot);
      } catch (RuntimeException exception) {
        if (isRetryable(exception)) {
          log.error("operation=walk-forward-oos-recovery studyId={} result=retryable_batch", row.studyId, exception);
          return;
        }
        log.error("operation=walk-forward-oos-recovery studyId={} errorCode={} result=skipped_invalid",
            row.studyId, errorCode(exception));
      }
    }
  }

  private void recoverIndividually(List<WalkForwardStudyRow> rows) {
    if (folds == null) throw new IllegalStateException("fold mapper is required for recovery fallback");
    for (WalkForwardStudyRow row : rows) {
      try {
        recoverOne(row, snapshots.load(row, folds.findAllByStudyId(row.studyId), true));
      } catch (RuntimeException exception) {
        if (isRetryable(exception)) {
          log.error("operation=walk-forward-oos-recovery studyId={} result=retryable_batch", row.studyId, exception);
          return;
        }
        log.error("operation=walk-forward-oos-recovery studyId={} errorCode={} result=skipped_invalid",
            row.studyId, errorCode(exception));
      }
    }
  }

  private void recoverOne(WalkForwardStudyRow row, WalkForwardStudySnapshot snapshot) {
    if (snapshot == null) throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "study snapshot missing");
    WalkForwardOosCalculation calculation = calculator.calculate(row, snapshot.folds(), snapshot.runs(), snapshot.equities());
    int affected = studies.backfillOosAggregate(row.studyId, row.status, row.updatedAt, calculation.successfulFolds(), calculation.failedFolds(),
        calculation.hasGaps(), calculation.totalReturnRatio(), calculation.maximumDrawdownRatio(), calculation.tradeCount(), calculation.totalFees(),
        calculation.parameterChanges(), (short) 1, Instant.now());
    if (affected > 1) throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "OOS recovery affected multiple rows");
    if (affected == 0) {
      WalkForwardStudyRow latest = studies.findByStudyId(row.studyId);
      if (latest == null || !Short.valueOf((short) 1).equals(latest.oosAggregateVersion))
        throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "OOS recovery CAS lost to an unfinished result");
      log.info("operation=walk-forward-oos-recovery studyId={} result=cas_lost_already_completed", row.studyId);
      return;
    }
    log.info("operation=walk-forward-oos-recovery studyId={} result=success version=1", row.studyId);
  }

  private boolean isRetryable(Throwable failure) {
    return WalkForwardOosRecoveryErrorClassifier.classify(failure)
        == WalkForwardOosRecoveryErrorClassifier.Kind.RETRYABLE_BATCH_ERROR;
  }

  private String errorCode(Throwable failure) {
    return failure instanceof WalkForwardTaskException task ? task.getErrorCode() : "WALK_FORWARD_OOS_RECOVERY_FAILED";
  }
}
