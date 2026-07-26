package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiprovider.config.quant.QuantWalkForwardProperties;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class WalkForwardStudyDispatcherTest {
  @Test
  void aggregatesBeforeAndAfterOnePhaseWithoutCallingPublicReads() {
    WalkForwardStudyMapper studies = mock(WalkForwardStudyMapper.class);
    WalkForwardFoldMapper folds = mock(WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardStudyService service = spy(realService(studies, folds, loader));
    WalkForwardStudyRow queued = study("QUEUED", BigDecimal.ZERO, null, null);
    WalkForwardStudyRow running = study("RUNNING", BigDecimal.ZERO, null, Instant.EPOCH);
    WalkForwardFoldRow pending = fold("PENDING", 0);
    WalkForwardFoldRow creating = fold("CREATING_EXPERIMENT", 0);
    when(studies.findNonTerminal()).thenReturn(List.of(queued));
    when(folds.resetStaleCreatingClaims(any(), any())).thenReturn(0);
    when(folds.findAllByStudyIds(any())).thenReturn(List.of(pending), List.of(creating));
    when(loader.loadMany(anyList(), anyList(), eq(false)))
        .thenReturn(Map.of("s", snapshot(queued, pending)))
        .thenReturn(Map.of("s", snapshot(running, creating)));
    when(folds.claimNextPending(eq("s"), anyString(), any())).thenReturn(1);
    when(studies.updateAggregate(eq("s"), eq("QUEUED"), eq("RUNNING"), eq(BigDecimal.ZERO), isNull(), isNull(), isNull(), any()))
        .thenReturn(1);
    when(studies.findByStudyId("s")).thenReturn(running);

    WalkForwardStudyDispatcher dispatcher = dispatcher(studies, folds, loader, service);
    dispatcher.tick();

    verify(folds).claimNextPending(eq("s"), anyString(), any());
    verify(service, times(2)).refreshAggregate(any(WalkForwardStudySnapshot.class));
    verify(service, never()).get(anyString());
    verify(service, never()).page(anyInt(), anyInt(), any(), any(), any());
  }

  @Test
  void globalBatchDatabaseFailureDoesNotFailAnyFold() {
    WalkForwardStudyMapper studies = mock(WalkForwardStudyMapper.class);
    WalkForwardFoldMapper folds = mock(WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardStudyService service = mock(WalkForwardStudyService.class);
    WalkForwardStudyRow first = study("QUEUED", BigDecimal.ZERO, null, null);
    WalkForwardStudyRow second = study("QUEUED", BigDecimal.ZERO, null, null); second.studyId = "s2";
    when(studies.findNonTerminal()).thenReturn(List.of(first, second));
    when(folds.resetStaleCreatingClaims(any(), any())).thenReturn(0);
    when(folds.findAllByStudyIds(any())).thenReturn(List.of());
    when(loader.loadMany(anyList(), anyList(), eq(false)))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));

    dispatcher(studies, folds, loader, service).tick();

    verify(folds, never()).markFailed(anyString(), anyString(), anyString(), any());
    verify(folds, never()).claimNextPending(anyString(), anyString(), any());
    verifyNoInteractions(service);
  }

  @Test
  void terminalAggregateIsWrittenAndNotReprocessedAfterCompletion() {
    WalkForwardStudyMapper studies = mock(WalkForwardStudyMapper.class);
    WalkForwardFoldMapper folds = mock(WalkForwardFoldMapper.class);
    WalkForwardStudySnapshotLoader loader = mock(WalkForwardStudySnapshotLoader.class);
    WalkForwardStudyService service = realService(studies, folds, loader);
    WalkForwardStudyRow running = study("RUNNING", BigDecimal.ZERO, "OLD", null);
    WalkForwardStudyRow failed = study("FAILED", BigDecimal.valueOf(100), "REAL_ERROR", Instant.EPOCH);
    failed.errorMessage = "real failure failedFolds=1";
    WalkForwardFoldRow fold = fold("FAILED", 0); fold.errorCode = "REAL_ERROR"; fold.errorMessage = "real failure";
    when(studies.findNonTerminal()).thenReturn(List.of(running));
    when(folds.resetStaleCreatingClaims(any(), any())).thenReturn(0);
    when(folds.findAllByStudyIds(any())).thenReturn(List.of(fold), List.of(fold));
    when(loader.loadMany(anyList(), anyList(), eq(false)))
        .thenReturn(Map.of("s", snapshot(running, fold)))
        .thenReturn(Map.of("s", snapshot(failed, fold)));
    when(studies.updateAggregate(eq("s"), eq("RUNNING"), eq("FAILED"), argThat(value -> value.compareTo(BigDecimal.valueOf(100)) == 0), eq("REAL_ERROR"), eq("real failure failedFolds=1"), any(Instant.class), any()))
        .thenReturn(1);
    when(studies.findByStudyId("s")).thenReturn(failed);

    dispatcher(studies, folds, loader, service).tick();

    verify(studies, times(1)).updateAggregate(eq("s"), eq("RUNNING"), eq("FAILED"), argThat(value -> value.compareTo(BigDecimal.valueOf(100)) == 0), eq("REAL_ERROR"), eq("real failure failedFolds=1"), any(Instant.class), any());
    verify(folds, never()).claimNextPending(anyString(), anyString(), any());
  }

  private WalkForwardStudyDispatcher dispatcher(WalkForwardStudyMapper studies, WalkForwardFoldMapper folds, WalkForwardStudySnapshotLoader loader, WalkForwardStudyService service) {
    return new WalkForwardStudyDispatcher(studies, folds, mock(BacktestExperimentCreationService.class), mock(BacktestRunMapper.class), new ObjectMapper(), new QuantWalkForwardProperties(), loader, service);
  }

  private WalkForwardStudyService realService(WalkForwardStudyMapper studies, WalkForwardFoldMapper folds, WalkForwardStudySnapshotLoader loader) {
    return new WalkForwardStudyService(studies, folds, loader, new ObjectMapper());
  }

  private WalkForwardStudySnapshot snapshot(WalkForwardStudyRow study, WalkForwardFoldRow fold) {
    return new WalkForwardStudySnapshot(study, List.of(fold), Map.of(), Map.of(), Map.of());
  }

  private WalkForwardStudyRow study(String status, BigDecimal progress, String errorCode, Instant finished) {
    WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = "s"; row.foldCount = 1; row.status = status; row.progressPercent = progress; row.errorCode = errorCode; row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH; row.finishedAt = finished; row.parameterGridJson = "{}"; return row;
  }

  private WalkForwardFoldRow fold(String status, int index) { WalkForwardFoldRow row = new WalkForwardFoldRow(); row.studyId = "s"; row.foldId = "f" + index; row.foldIndex = index; row.status = status; row.experimentId = "e" + index; return row; }
}
