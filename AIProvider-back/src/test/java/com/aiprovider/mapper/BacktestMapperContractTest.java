package com.aiprovider.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestMapperContractTest {
  @Test
  void backtestMappersDoNotUseSelectStar() {
    for (String file :
        List.of(
            "BacktestRunMapper.java", "BacktestTradeMapper.java", "BacktestEquityMapper.java")) {
      try {
        String source = Files.readString(Path.of("src/main/java/com/aiprovider/mapper", file));
        assertFalse(source.toUpperCase().contains("SELECT *"), file);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }

  @Test
  void queueRejectedRetryIsDatabaseCas() {
    try {
      String source =
          Files.readString(Path.of("src/main/java/com/aiprovider/mapper/BacktestRunMapper.java"));
      assertTrue(source.contains("retryQueueRejectedRun"));
      assertTrue(source.contains("Status='FAILED' AND ErrorCode='BACKTEST_QUEUE_FULL'"));
      assertTrue(source.contains("FinishedAt=NULL"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
