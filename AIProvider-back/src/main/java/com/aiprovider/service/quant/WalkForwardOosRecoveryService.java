package com.aiprovider.service.quant;

import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class WalkForwardOosRecoveryService {
  private static final Logger log = LogManager.getLogger(WalkForwardOosRecoveryService.class);
  private final WalkForwardStudyMapper studies;
  private final WalkForwardStudySnapshotLoader snapshots;
  private final WalkForwardOosCalculator calculator;

  public WalkForwardOosRecoveryService(WalkForwardStudyMapper studies, WalkForwardStudySnapshotLoader snapshots,
      WalkForwardOosCalculator calculator) {
    this.studies = studies; this.snapshots = snapshots; this.calculator = calculator;
  }

  public void recoverBatch(int limit) {
    List<WalkForwardStudyRow> rows = studies.findTerminalMissingOosAggregate(limit);
    if (rows.isEmpty()) return;
    Map<String, WalkForwardStudySnapshot> loaded = snapshots.loadMany(rows, true);
    for (WalkForwardStudyRow row : rows) {
      try {
        WalkForwardStudySnapshot snapshot = loaded.get(row.studyId);
        if (snapshot == null) throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "study snapshot missing");
        WalkForwardOosCalculation calculation = calculator.calculate(row, snapshot.folds(), snapshot.runs(), snapshot.equities());
        int affected = studies.backfillOosAggregate(row.studyId, row.status, row.updatedAt, calculation.successfulFolds(), calculation.failedFolds(),
            calculation.hasGaps(), calculation.totalReturnRatio(), calculation.maximumDrawdownRatio(), calculation.tradeCount(), calculation.totalFees(),
            calculation.parameterChanges(), (short) 1, Instant.now());
        if (affected > 1) throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "OOS recovery affected multiple rows");
        if (affected == 0) {
          WalkForwardStudyRow latest = studies.findByStudyId(row.studyId);
          if (latest == null || !Short.valueOf((short) 1).equals(latest.oosAggregateVersion)) {
            throw new WalkForwardTaskException("WALK_FORWARD_STATE_CONFLICT", "OOS recovery CAS lost to an unfinished result");
          }
        }
        log.info("operation=walk-forward-oos-recovery studyId={} result=success version=1", row.studyId);
      } catch (WalkForwardTaskException exception) {
        log.error("operation=walk-forward-oos-recovery studyId={} errorCode={} result=retryable", row.studyId, exception.getErrorCode());
      }
    }
  }
}
