package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class QuantWalkForwardMySqlTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("aiprovider_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void flywayV70CreatesStudyFoldSchemaAndRealFoldCas() throws Exception {
    try (TestContext context = open()) {
      JdbcTemplate jdbc = context.jdbc;
      assertEquals(1, tableCount(jdbc, "q_walk_forward_study"));
      assertEquals(1, tableCount(jdbc, "q_walk_forward_fold"));
      assertEquals("JSON", columnType(jdbc, "q_walk_forward_study", "ParameterGridJson"));
      assertEquals("decimal(38,18)", columnType(jdbc, "q_walk_forward_study", "OrderAmount"));
      assertTrue(indexExists(jdbc, "q_walk_forward_fold", "uk_walk_forward_study_fold"));
      assertTrue(indexExists(jdbc, "q_walk_forward_fold", "uk_walk_forward_experiment_id"));

      String studyId = UUID.randomUUID().toString();
      WalkForwardStudyRow study = study(studyId);
      assertEquals(1, context.studies.insert(study));
      WalkForwardFoldRow first = fold(studyId, 0), second = fold(studyId, 1);
      assertEquals(2, context.folds.insertBatch(List.of(first, second)));

      ExecutorService callers = Executors.newFixedThreadPool(2);
      try {
        Callable<Integer> claim =
            () ->
                context.folds.claimNextPending(
                    studyId, UUID.randomUUID().toString(), Instant.now());
        List<Future<Integer>> results = callers.invokeAll(List.of(claim, claim));
        assertEquals(1, results.get(0).get() + results.get(1).get());
      } finally {
        callers.shutdownNow();
      }
      assertEquals(
          1, context.folds.resetStaleCreatingClaims(Instant.now().plusSeconds(1), Instant.now()));
      assertEquals("PENDING", context.folds.findAllByStudyId(studyId).get(0).status);
    }
  }

  @Test
  void realTrainingProjectionCannotLeakValidationMetrics() {
    try (TestContext context = open()) {
      String experimentId = UUID.randomUUID().toString();
      BacktestExperimentCandidateRow a = candidate(experimentId, 0), b = candidate(experimentId, 1);
      assertEquals(2, context.candidates.insertBatch(List.of(a, b)));
      context.jdbc.update(
          "UPDATE q_backtest_experiment_candidate SET DispatchStatus='DISPATCHED' WHERE"
              + " ExperimentId=?",
          experimentId);
      insertRuns(
          context.runs, a.trainingRunId, a.validationRunId, b.trainingRunId, b.validationRunId);
      context.jdbc.update(
          "UPDATE q_backtest_run SET"
              + " Status='COMPLETED',TradeCount=10,TotalReturnRatio=?,NetProfit=?,ProfitFactor=?,WinRate=?,MaximumDrawdownRatio=? WHERE RunId=?",
          new BigDecimal("0.80"),
          new BigDecimal("8"),
          new BigDecimal("2"),
          new BigDecimal("0.70"),
          new BigDecimal("0.10"),
          a.trainingRunId);
      context.jdbc.update(
          "UPDATE q_backtest_run SET"
              + " Status='COMPLETED',TradeCount=10,TotalReturnRatio=?,NetProfit=?,ProfitFactor=?,WinRate=?,MaximumDrawdownRatio=? WHERE RunId=?",
          new BigDecimal("0.10"),
          new BigDecimal("1"),
          new BigDecimal("1"),
          new BigDecimal("0.20"),
          new BigDecimal("0.90"),
          a.validationRunId);
      context.jdbc.update(
          "UPDATE q_backtest_run SET"
              + " Status='COMPLETED',TradeCount=10,TotalReturnRatio=?,NetProfit=?,ProfitFactor=?,WinRate=?,MaximumDrawdownRatio=? WHERE RunId=?",
          new BigDecimal("0.20"),
          new BigDecimal("2"),
          new BigDecimal("3"),
          new BigDecimal("0.80"),
          new BigDecimal("0.20"),
          b.trainingRunId);
      context.jdbc.update(
          "UPDATE q_backtest_run SET"
              + " Status='COMPLETED',TradeCount=10,TotalReturnRatio=?,NetProfit=?,ProfitFactor=?,WinRate=?,MaximumDrawdownRatio=? WHERE RunId=?",
          new BigDecimal("0.90"),
          new BigDecimal("9"),
          new BigDecimal("4"),
          new BigDecimal("0.95"),
          new BigDecimal("0.01"),
          b.validationRunId);

      WalkForwardTrainingCandidateRow selected =
          context.folds.findBestByTrainTotalReturnRatio(experimentId, 10);
      assertNotNull(selected);
      assertEquals(a.candidateId, selected.candidateId);
      assertEquals(b.candidateId, context.folds.findBestByTrainProfitFactor(experimentId, 10).candidateId);
      assertEquals(b.candidateId, context.folds.findBestByTrainNetProfit(experimentId, 10).candidateId);
      assertEquals(b.candidateId, context.folds.findBestByTrainWinRate(experimentId, 10).candidateId);
      assertEquals(a.candidateId, context.folds.findBestByTrainMaximumDrawdownRatio(experimentId, 10).candidateId);
    }
  }

  private static WalkForwardStudyRow study(String id) {
    WalkForwardStudyRow row = new WalkForwardStudyRow();
    row.studyId = id;
    row.datasetId = 1;
    row.provider = "BINANCE_USDM";
    row.marketType = "USDM_PERPETUAL";
    row.dataType = "CANDLE";
    row.symbol = "BTCUSDT";
    row.intervalCode = "1m";
    row.strategyCode = "EMA_CROSS_LONG_ONLY";
    row.strategyVersion = "1.0.0";
    row.executionProfileCode = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
    row.directionMode = "LONG_ONLY";
    row.orderSizingMode = "BASE_QUANTITY";
    row.parameterGridJson = "{\"fastPeriod\":[5],\"slowPeriod\":[20]}";
    row.windowMode = "ROLLING";
    row.studyStartOpenTimeMs = 0;
    row.studyEndOpenTimeMs = 120000;
    row.trainingBars = 1;
    row.validationBars = 1;
    row.stepBars = 1;
    row.foldCount = 2;
    row.candidateCountPerFold = 1;
    row.totalChildRuns = 4;
    row.selectionMetric = "TRAIN_TOTAL_RETURN_RATIO";
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = new BigDecimal("0.001");
    row.createdAt = Instant.now();
    row.updatedAt = row.createdAt;
    return row;
  }

  private static WalkForwardFoldRow fold(String studyId, int index) {
    WalkForwardFoldRow row = new WalkForwardFoldRow();
    row.foldId = UUID.randomUUID().toString();
    row.studyId = studyId;
    row.foldIndex = index;
    row.trainingStartOpenTimeMs = index * 60_000L;
    row.trainingEndOpenTimeMs = (index + 1) * 60_000L;
    row.validationStartOpenTimeMs = row.trainingEndOpenTimeMs;
    row.validationEndOpenTimeMs = (index + 2) * 60_000L;
    row.experimentId = UUID.randomUUID().toString();
    row.createdAt = Instant.now();
    row.updatedAt = row.createdAt;
    return row;
  }

  private static BacktestExperimentCandidateRow candidate(String experimentId, int index) {
    BacktestExperimentCandidateRow row = new BacktestExperimentCandidateRow();
    row.candidateId = UUID.randomUUID().toString();
    row.experimentId = experimentId;
    row.candidateIndex = index;
    row.parametersJson =
        index == 0
            ? "{\"fastPeriod\":5,\"slowPeriod\":20}"
            : "{\"fastPeriod\":7,\"slowPeriod\":20}";
    row.trainingRunId = UUID.randomUUID().toString();
    row.validationRunId = UUID.randomUUID().toString();
    row.createdAt = Instant.now();
    row.updatedAt = row.createdAt;
    return row;
  }

  private static void insertRuns(BacktestRunMapper runs, String... ids) {
    for (String id : ids) {
      BacktestRunRow row = new BacktestRunRow();
      row.runId = id;
      row.datasetId = 1;
      row.provider = "BINANCE_USDM";
      row.marketType = "USDM_PERPETUAL";
      row.dataType = "CANDLE";
      row.symbol = "BTCUSDT";
      row.intervalCode = "1m";
      row.startOpenTimeMs = 0;
      row.endOpenTimeExclusiveMs = 60000;
      row.strategyCode = "EMA_CROSS_LONG_ONLY";
      row.strategyVersion = "1.0.0";
      row.executionProfileCode = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
      row.directionMode = "LONG_ONLY";
      row.orderSizingMode = "BASE_QUANTITY";
      row.requestedParametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
      row.orderAmount = BigDecimal.ONE;
      row.feeRate = new BigDecimal("0.001");
      row.forceCloseAtEnd = true;
      row.queuedAt = Instant.now();
      row.updatedAt = row.queuedAt;
      assertEquals(1, runs.insert(row));
    }
  }

  private static TestContext open() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    AnnotationConfigApplicationContext application = new AnnotationConfigApplicationContext();
    TestMyBatisConfiguration.DATA_SOURCE = dataSource;
    application.register(TestMyBatisConfiguration.class);
    application.refresh();
    return new TestContext(application, dataSource);
  }

  private static int tableCount(JdbcTemplate jdbc, String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
            + " table_name=?",
        Integer.class,
        table);
  }

  private static String columnType(JdbcTemplate jdbc, String table, String column) {
    return jdbc.queryForObject(
        "SELECT CASE WHEN DATA_TYPE='decimal' THEN"
            + " LOWER(CONCAT(DATA_TYPE,'(',NUMERIC_PRECISION,',',NUMERIC_SCALE,')')) ELSE"
            + " UPPER(DATA_TYPE) END FROM information_schema.columns WHERE table_schema=DATABASE()"
            + " AND table_name=? AND column_name=?",
        String.class,
        table,
        column);
  }

  private static boolean indexExists(JdbcTemplate jdbc, String table, String index) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND"
                + " table_name=? AND index_name=?",
            Integer.class,
            table,
            index)
        > 0;
  }

  private static final class TestContext implements AutoCloseable {
    final AnnotationConfigApplicationContext application;
    final JdbcTemplate jdbc;
    final WalkForwardStudyMapper studies;
    final WalkForwardFoldMapper folds;
    final BacktestExperimentCandidateMapper candidates;
    final BacktestRunMapper runs;

    TestContext(AnnotationConfigApplicationContext application, DataSource dataSource) {
      this.application = application;
      jdbc = new JdbcTemplate(dataSource);
      studies = application.getBean(WalkForwardStudyMapper.class);
      folds = application.getBean(WalkForwardFoldMapper.class);
      candidates = application.getBean(BacktestExperimentCandidateMapper.class);
      runs = application.getBean(BacktestRunMapper.class);
    }

    public void close() {
      application.close();
    }
  }

  @Configuration
  @MapperScan(
      basePackageClasses = {
        WalkForwardStudyMapper.class,
        WalkForwardFoldMapper.class,
        BacktestExperimentCandidateMapper.class,
        BacktestRunMapper.class
      })
  static class TestMyBatisConfiguration {
    static DataSource DATA_SOURCE;

    @Bean
    DataSource dataSource() {
      return DATA_SOURCE;
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
      SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
      factory.setDataSource(dataSource);
      factory.afterPropertiesSet();
      return factory.getObject();
    }
  }
}
