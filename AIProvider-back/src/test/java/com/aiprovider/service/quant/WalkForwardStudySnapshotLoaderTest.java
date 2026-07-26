package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WalkForwardStudySnapshotLoaderTest {
  @Test
  void loadsMultipleStudiesWithBatchQueriesOnly() {
    WalkForwardFoldMapper foldMapper = mock(WalkForwardFoldMapper.class);
    BacktestExperimentService experimentService = mock(BacktestExperimentService.class);
    BacktestRunMapper runMapper = mock(BacktestRunMapper.class);
    BacktestEquityMapper equityMapper = mock(BacktestEquityMapper.class);
    WalkForwardStudySnapshotLoader loader =
        new WalkForwardStudySnapshotLoader(foldMapper, experimentService, runMapper, equityMapper);

    WalkForwardStudyRow first = study("s1"), second = study("s2");
    WalkForwardFoldRow firstFold = fold("s1", "e1", "t1", "v1");
    WalkForwardFoldRow secondFold = fold("s2", "e2", "t2", "v2");
    when(experimentService.getMany(any())).thenReturn(Map.of());
    when(runMapper.findByRunIds(any())).thenReturn(List.of(run("t1"), run("v1"), run("t2"), run("v2")));
    when(equityMapper.findAllByRunIds(any())).thenReturn(List.of(equity("v1"), equity("v2")));

    Map<String, WalkForwardStudySnapshot> result =
        loader.loadMany(List.of(first, second), List.of(firstFold, secondFold), true);

    assertEquals(2, result.size());
    assertEquals("t1", result.get("s1").run("t1").runId);
    assertEquals("v2", result.get("s2").equity("v2").get(0).runId);
    verify(experimentService, times(1)).getMany(any());
    verify(runMapper, times(1)).findByRunIds(any());
    verify(equityMapper, times(1)).findAllByRunIds(any());
    verifyNoMoreInteractions(experimentService, runMapper, equityMapper);
    verifyNoInteractions(foldMapper);
  }

  private static WalkForwardStudyRow study(String id) { WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = id; row.foldCount = 1; return row; }
  private static WalkForwardFoldRow fold(String study, String experiment, String train, String validation) { WalkForwardFoldRow row = new WalkForwardFoldRow(); row.studyId = study; row.experimentId = experiment; row.selectedTrainingRunId = train; row.selectedValidationRunId = validation; row.status = "COMPLETED"; return row; }
  private static BacktestRunRow run(String id) { BacktestRunRow row = new BacktestRunRow(); row.runId = id; return row; }
  private static BacktestEquityRow equity(String id) { BacktestEquityRow row = new BacktestEquityRow(); row.runId = id; return row; }
}
