package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiprovider.config.quant.QuantResearchProperties;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchStudyAggregationSchedulerTest {
  @Test void casLossReloadsParentAndDoesNotMarkItFailed() {
    ResearchStudyMapper research = mock(ResearchStudyMapper.class); WalkForwardStudyMapper walkForward = mock(WalkForwardStudyMapper.class);
    ResearchStudyRow parent = parent(); WalkForwardStudyRow child = child("RUNNING");
    when(research.findNonTerminal(50)).thenReturn(List.of(parent)); when(walkForward.findByStudyIds(List.of("child"))).thenReturn(List.of(child));
    when(research.updateAggregate(anyString(), anyString(), any(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(0);
    when(research.findByResearchStudyId("research")).thenReturn(parent);
    new ResearchStudyAggregationScheduler(research, walkForward, new QuantResearchProperties()).tick();
    verify(research).findByResearchStudyId("research");
    verify(research, never()).updateAggregate(eq("research"), eq("QUEUED"), eq(Instant.EPOCH), eq("FAILED"), any(), any(), any(), any(), any(), any());
  }

  @Test void affectedMoreThanOneIsPersistenceFailure() {
    ResearchStudyMapper research = mock(ResearchStudyMapper.class); WalkForwardStudyMapper walkForward = mock(WalkForwardStudyMapper.class);
    ResearchStudyRow parent = parent(); WalkForwardStudyRow child = child("RUNNING");
    when(research.updateAggregate(anyString(), anyString(), any(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(2);
    ResearchStudyAggregationScheduler scheduler = new ResearchStudyAggregationScheduler(research, walkForward, new QuantResearchProperties());
    assertEquals("RESEARCH_PERSISTENCE_FAILED", assertThrows(ResearchStudyTaskException.class, () -> scheduler.update(parent, child)).getErrorCode());
  }

  private ResearchStudyRow parent() { ResearchStudyRow row = new ResearchStudyRow(); row.researchStudyId = "research"; row.walkForwardStudyId = "child"; row.status = "QUEUED"; row.updatedAt = Instant.EPOCH; return row; }
  private WalkForwardStudyRow child(String status) { WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = "child"; row.status = status; row.progressPercent = BigDecimal.TEN; row.startedAt = Instant.EPOCH; return row; }
}
