package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiprovider.mapper.WalkForwardStudyMapper;
import com.aiprovider.mapper.WalkForwardFoldMapper;
import com.aiprovider.mapper.row.WalkForwardStudyRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WalkForwardOosRecoveryServiceTest {
  @Test void backfillsHistoricalTerminalStudyWithoutHttp() {
    WalkForwardStudyMapper mapper = mock(WalkForwardStudyMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardOosCalculator calculator = mock(WalkForwardOosCalculator.class);
    WalkForwardStudyRow row = row();
    when(mapper.findTerminalMissingOosAggregate(20)).thenReturn(List.of(row));
    when(loader.loadMany(List.of(row), true)).thenReturn(Map.of("s", new WalkForwardStudySnapshot(row, List.of(), Map.of(), Map.of(), Map.of())));
    when(calculator.calculate(eq(row), anyList(), anyMap(), anyMap())).thenReturn(new WalkForwardOosCalculation(1, 0, false, 2, new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"), 0, List.of()));
    when(mapper.backfillOosAggregate(eq("s"), eq("COMPLETED"), eq(Instant.EPOCH), eq(1), eq(0), eq(false), any(), any(), eq(2), any(), eq(0), eq((short) 1), any())).thenReturn(1);
    new WalkForwardOosRecoveryService(mapper, loader, calculator).recoverBatch(20);
    verify(mapper).backfillOosAggregate(eq("s"), eq("COMPLETED"), eq(Instant.EPOCH), eq(1), eq(0), eq(false), any(), any(), eq(2), any(), eq(0), eq((short) 1), any());
  }

  @Test void casLossAcceptsOtherInstanceAlreadyAtVersionOne() {
    WalkForwardStudyMapper mapper = mock(WalkForwardStudyMapper.class); WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class); WalkForwardOosCalculator calculator = mock(WalkForwardOosCalculator.class);
    WalkForwardStudyRow row = row(); WalkForwardStudyRow latest = row(); latest.oosAggregateVersion = 1;
    when(mapper.findTerminalMissingOosAggregate(20)).thenReturn(List.of(row)); when(loader.loadMany(anyList(), eq(true))).thenReturn(Map.of("s", new WalkForwardStudySnapshot(row, List.of(), Map.of(), Map.of(), Map.of())));
    when(calculator.calculate(any(), anyList(), anyMap(), anyMap())).thenReturn(new WalkForwardOosCalculation(1, 0, false, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of()));
    when(mapper.backfillOosAggregate(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0); when(mapper.findByStudyId("s")).thenReturn(latest);
    assertDoesNotThrow(() -> new WalkForwardOosRecoveryService(mapper, loader, calculator).recoverBatch(20));
  }

  @Test void permanentBatchFailureFallsBackAndDoesNotBlockLaterStudies() {
    WalkForwardStudyMapper mapper = mock(WalkForwardStudyMapper.class);
    com.aiprovider.mapper.WalkForwardFoldMapper foldMapper = mock(com.aiprovider.mapper.WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardOosCalculator calculator = mock(WalkForwardOosCalculator.class);
    WalkForwardStudyRow first = row(); first.studyId = "a";
    WalkForwardStudyRow second = row(); second.studyId = "b";
    when(mapper.findTerminalMissingOosAggregate(20)).thenReturn(List.of(first, second));
    when(loader.loadMany(anyList(), eq(true))).thenThrow(new IllegalStateException("bad study snapshot"));
    when(foldMapper.findAllByStudyId(anyString())).thenReturn(List.of());
    when(loader.load(any(), anyList(), eq(true))).thenReturn(new WalkForwardStudySnapshot(first, List.of(), Map.of(), Map.of(), Map.of()));
    when(calculator.calculate(any(), anyList(), anyMap(), anyMap())).thenReturn(new WalkForwardOosCalculation(0, 1, true, null, null, null, null, null, List.of()));
    when(mapper.backfillOosAggregate(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    new WalkForwardOosRecoveryService(mapper, foldMapper, loader, calculator).recoverBatch(20);
    verify(mapper).backfillOosAggregate(eq("a"), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(mapper).backfillOosAggregate(eq("b"), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test void retryableBatchFailureStopsBeforeFallback() {
    WalkForwardStudyMapper mapper = mock(WalkForwardStudyMapper.class);
    WalkForwardFoldMapper foldMapper = mock(WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardOosCalculator calculator = mock(WalkForwardOosCalculator.class);
    when(mapper.findTerminalMissingOosAggregate(20)).thenReturn(List.of(row()));
    when(loader.loadMany(anyList(), eq(true))).thenThrow(new DataAccessResourceFailureException("db unavailable"));
    new WalkForwardOosRecoveryService(mapper, foldMapper, loader, calculator).recoverBatch(20);
    verify(mapper, never()).backfillOosAggregate(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(foldMapper);
  }

  private WalkForwardStudyRow row() { WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = "s"; row.status = "COMPLETED"; row.updatedAt = Instant.EPOCH; row.foldCount = 0; return row; }
}
