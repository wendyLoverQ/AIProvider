package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiprovider.controller.quant.dto.ResearchStudyDtos;
import com.aiprovider.mapper.ResearchStudyMapper;
import com.aiprovider.mapper.row.ResearchStudyRow;
import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchStudyServiceTest {
  @Test void nonTerminalSummaryKeepsAllOosFieldsNull() {
    ResearchStudyRow row = row("QUEUED");
    ResearchStudyDtos.Summary summary = new ResearchStudyService(mock(ResearchStudyMapper.class), new ObjectMapper()).summaries(List.of(row)).get(0);
    assertNull(summary.successfulOosFolds()); assertNull(summary.failedFolds()); assertNull(summary.hasOosGaps());
    assertNull(summary.oosTotalReturnRatio()); assertNull(summary.oosMaximumDrawdownRatio()); assertNull(summary.oosTradeCount());
    assertNull(summary.oosTotalFees()); assertNull(summary.parameterChanges());
  }

  @Test void comparisonResultsUseOnePageQueryAndOneCountQuery() {
    ResearchStudyMapper mapper = mock(ResearchStudyMapper.class);
    when(mapper.findComparisonResultsPage(anyString(), eq("OOS_TOTAL_RETURN_RATIO"), eq(true), eq(2), eq(2L))).thenReturn(List.of(row("QUEUED")));
    when(mapper.countByComparisonGroupKey(anyString())).thenReturn(6L);
    BacktestDtos.Page<ResearchStudyDtos.Summary> page = new ResearchStudyService(mapper, new ObjectMapper()).results("A".repeat(64), 2, 2, null, null);
    assertEquals(1, page.records().size()); assertEquals(6L, page.total());
    verify(mapper).findComparisonResultsPage("a".repeat(64), "OOS_TOTAL_RETURN_RATIO", true, 2, 2L);
    verify(mapper).countByComparisonGroupKey("a".repeat(64));
    verifyNoMoreInteractions(mapper);
  }

  @Test void terminalMissingOosResultIsInvalid() {
    ResearchStudyRow row = row("COMPLETED");
    assertEquals("RESEARCH_RESULT_INVALID", assertThrows(ResearchStudyTaskException.class,
        () -> new ResearchStudyService(mock(ResearchStudyMapper.class), new ObjectMapper()).summaries(List.of(row))).getErrorCode());
  }

  private ResearchStudyRow row(String status) {
    ResearchStudyRow row = new ResearchStudyRow(); row.researchStudyId = "id"; row.name = "name"; row.datasetId = 1;
    row.parameterSpaceJson = "{}"; row.expandedParameterGridJson = "{}"; row.status = status; row.progressPercent = BigDecimal.ZERO;
    row.comparisonGroupKey = "a".repeat(64); row.walkForwardStudyId = "child"; row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH;
    return row;
  }
}
