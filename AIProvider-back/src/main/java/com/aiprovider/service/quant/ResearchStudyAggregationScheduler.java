package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantResearchProperties;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ResearchStudyAggregationScheduler {
  private static final Logger log = LogManager.getLogger(ResearchStudyAggregationScheduler.class);
  private final ResearchStudyMapper research;
  private final WalkForwardStudyMapper walkForward;
  private final QuantResearchProperties properties;

  public ResearchStudyAggregationScheduler(ResearchStudyMapper research, WalkForwardStudyMapper walkForward, QuantResearchProperties properties) {
    this.research = research; this.walkForward = walkForward; this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${quant.research.aggregation-interval-ms:3000}")
  public void tick() {
    List<ResearchStudyRow> parents = research.findNonTerminal(properties.getBatchSize());
    if (parents.isEmpty()) return;
    Map<String, WalkForwardStudyRow> children = new LinkedHashMap<>();
    for (WalkForwardStudyRow child : walkForward.findByStudyIds(parents.stream().map(row -> row.walkForwardStudyId).toList())) children.put(child.studyId, child);
    for (ResearchStudyRow parent : parents) {
      WalkForwardStudyRow child = children.get(parent.walkForwardStudyId);
      if (child == null) {
        log.error("operation=research-aggregate researchStudyId={} errorCode=RESEARCH_CHILD_CONFLICT result=failed", parent.researchStudyId);
        persistChildConflict(parent);
        continue;
      }
      try { update(parent, child); }
      catch (RuntimeException exception) { log.error("operation=research-aggregate researchStudyId={} result=retryable", parent.researchStudyId, exception); }
    }
  }

  void update(ResearchStudyRow parent, WalkForwardStudyRow child) {
    String status = switch (child.status) {
      case "QUEUED" -> "QUEUED";
      case "RUNNING" -> "RUNNING";
      case "COMPLETED" -> "COMPLETED";
      case "COMPLETED_WITH_FAILURES" -> "COMPLETED_WITH_FAILURES";
      case "FAILED" -> "FAILED";
      default -> throw new ResearchStudyTaskException("RESEARCH_RESULT_INVALID", "unknown walk-forward status=" + child.status);
    };
    BigDecimal progress = child.progressPercent == null ? BigDecimal.ZERO : child.progressPercent;
    Instant finished = terminal(status) ? child.finishedAt : null;
    String errorCode = terminal(status) && ("FAILED".equals(status) || "COMPLETED_WITH_FAILURES".equals(status)) ? child.errorCode : null;
    String errorMessage = terminal(status) && ("FAILED".equals(status) || "COMPLETED_WITH_FAILURES".equals(status)) ? child.errorMessage : null;
    int affected = research.updateAggregate(parent.researchStudyId, parent.status, parent.updatedAt, status, progress, errorCode, errorMessage, child.startedAt, finished, Instant.now());
    if (affected > 1) throw new ResearchStudyTaskException("RESEARCH_PERSISTENCE_FAILED", "research aggregate affected multiple rows");
    if (affected == 0) {
      ResearchStudyRow latest = research.findByResearchStudyId(parent.researchStudyId);
      if (latest == null) throw new ResearchStudyTaskException("RESEARCH_CHILD_CONFLICT", "research study disappeared during aggregation");
      log.info("operation=research-aggregate researchStudyId={} result=cas_lost currentStatus={}", parent.researchStudyId, latest.status);
    }
  }

  private void persistChildConflict(ResearchStudyRow parent) {
    int affected = research.updateAggregate(parent.researchStudyId, parent.status, parent.updatedAt, "FAILED", BigDecimal.valueOf(100),
        "RESEARCH_CHILD_CONFLICT", "walk-forward child is missing", Instant.now(), null, Instant.now());
    if (affected > 1) throw new ResearchStudyTaskException("RESEARCH_PERSISTENCE_FAILED", "research aggregate affected multiple rows");
    if (affected == 0) research.findByResearchStudyId(parent.researchStudyId);
  }

  private boolean terminal(String status) { return "COMPLETED".equals(status) || "COMPLETED_WITH_FAILURES".equals(status) || "FAILED".equals(status); }
}
