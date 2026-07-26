package com.aiprovider.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class BacktestExperimentMapperContractTest {
  @Test
  void experimentMappersUseExplicitColumnsAndDatabaseCas() throws Exception {
    String experiment =
        Files.readString(
            Path.of("src/main/java/com/aiprovider/mapper/BacktestExperimentMapper.java"));
    String candidate =
        Files.readString(
            Path.of("src/main/java/com/aiprovider/mapper/BacktestExperimentCandidateMapper.java"));
    assertFalse((experiment + candidate).toUpperCase().contains("SELECT *"));
    assertTrue(candidate.contains("DispatchStatus='PENDING'"));
    assertTrue(candidate.contains("DispatchStatus='CLAIMED'"));
    assertTrue(candidate.contains("releaseClaimToPending"));
    assertTrue(candidate.contains("ErrorCode=NULL"));
    assertTrue(candidate.contains("ClaimToken=#{claimToken}"));
    assertTrue(
        Files.exists(
            Path.of("src/main/resources/db/migration/V69__quant_backtest_experiments.sql")));
  }
}
