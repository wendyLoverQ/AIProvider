package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class WalkForwardStudyAggregationTest {
  private final WalkForwardStudyService service =
      new WalkForwardStudyService(
          mock(WalkForwardStudyMapper.class),
          mock(WalkForwardFoldMapper.class),
          mock(WalkForwardStudySnapshotLoader.class),
          new ObjectMapper());

  @Test
  void enforcesTheCompleteStudyStateMatrix() throws Exception {
    assertEquals("QUEUED", state("PENDING", "PENDING").status());
    assertEquals("RUNNING", state("CREATING_EXPERIMENT", "PENDING").status());
    assertEquals("RUNNING", stateWithWaiting().status());
    assertEquals("RUNNING", state("COMPLETED", "PENDING").status());
    AggregateView runningWithFailure = state("FAILED", "PENDING");
    assertEquals("RUNNING", runningWithFailure.status());
    assertNull(runningWithFailure.errorCode());
    assertEquals("COMPLETED", state("COMPLETED", "COMPLETED").status());
    AggregateView mixed = state("FAILED", "COMPLETED");
    assertEquals("COMPLETED_WITH_FAILURES", mixed.status());
    assertEquals("WALK_FORWARD_NO_ELIGIBLE_CANDIDATE", mixed.errorCode());
    assertEquals("FAILED", state("FAILED", "FAILED").status());
    assertEquals(0, new BigDecimal("100").compareTo(state("COMPLETED", "COMPLETED").progress()));
  }

  @Test
  void rejectsUnknownFoldState() {
    InvocationTargetException error =
        assertThrows(InvocationTargetException.class, () -> invoke(snapshot("UNKNOWN", "PENDING", Map.of())));
    assertEquals("WALK_FORWARD_STATE_CONFLICT", ((WalkForwardTaskException) error.getCause()).getErrorCode());
  }

  private AggregateView state(String first, String second) throws Exception {
    return view(invoke(snapshot(first, second, Map.of())));
  }

  private AggregateView stateWithWaiting() throws Exception {
    return view(invoke(snapshot("WAITING_EXPERIMENT", "PENDING", Map.of("e0", experiment()))));
  }

  private Object invoke(WalkForwardStudySnapshot snapshot) throws Exception {
    Method method = WalkForwardStudyService.class.getDeclaredMethod("aggregate", WalkForwardStudySnapshot.class);
    method.setAccessible(true);
    return method.invoke(service, snapshot);
  }

  private AggregateView view(Object value) throws Exception {
    Class<?> type = value.getClass();
    return new AggregateView(
        (String) type.getDeclaredMethod("status").invoke(value),
        (BigDecimal) type.getDeclaredMethod("progress").invoke(value),
        (String) type.getDeclaredMethod("errorCode").invoke(value));
  }

  private WalkForwardStudySnapshot snapshot(String first, String second, Map<String, BacktestExperimentDtos.ExperimentSummary> experiments) {
    WalkForwardStudyRow study = new WalkForwardStudyRow(); study.studyId = "s"; study.foldCount = 2;
    return new WalkForwardStudySnapshot(study, List.of(fold(first, 0), fold(second, 1)), experiments, Map.of(), Map.of());
  }

  private WalkForwardFoldRow fold(String status, int index) {
    WalkForwardFoldRow fold = new WalkForwardFoldRow(); fold.studyId = "s"; fold.foldId = "f" + index; fold.foldIndex = index; fold.status = status; fold.experimentId = "e" + index;
    if ("FAILED".equals(status)) { fold.errorCode = index == 0 ? "WALK_FORWARD_NO_ELIGIBLE_CANDIDATE" : "WALK_FORWARD_SELECTED_VALIDATION_FAILED"; fold.errorMessage = "failure"; }
    return fold;
  }

  private BacktestExperimentDtos.ExperimentSummary experiment() {
    return new BacktestExperimentDtos.ExperimentSummary("e0", 1L, null, null, null, null, null, null, null, Map.of(), 0, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, BigDecimal.ONE, BigDecimal.ZERO, true, "RUNNING", new BigDecimal("40"), 0, 1, 0, 0, 0, 0, null, null, Instant.EPOCH, null, null, Instant.EPOCH);
  }

  private record AggregateView(String status, BigDecimal progress, String errorCode) {}
}
