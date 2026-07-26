package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiprovider.mapper.BacktestExperimentCandidateMapper;
import com.aiprovider.mapper.BacktestExperimentMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.row.BacktestExperimentCandidateRow;
import com.aiprovider.mapper.row.BacktestExperimentRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class QuantExperimentMySqlMigrationIT {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.36")
          .withDatabaseName("aiprovider_test")
          .withUsername("test")
          .withPassword("test");

  @Test
  void flywayV1ToV69CreatesTheQuantExperimentSchemaInMySql() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(1, tableCount(jdbc, "q_backtest_experiment"));
    assertEquals(1, tableCount(jdbc, "q_backtest_experiment_candidate"));
    assertEquals("JSON", columnType(jdbc, "q_backtest_experiment", "ParameterGridJson"));
    assertEquals("JSON", columnType(jdbc, "q_backtest_experiment_candidate", "ParametersJson"));
    assertEquals("decimal(38,18)", columnType(jdbc, "q_backtest_experiment", "OrderAmount"));
    assertEquals("decimal(38,18)", columnType(jdbc, "q_backtest_experiment", "FeeRate"));
    assertTrue(indexExists(jdbc, "q_backtest_experiment", "uq_q_backtest_experiment_id"));
    assertTrue(indexExists(jdbc, "q_backtest_experiment", "ix_q_backtest_experiment_status"));
    assertTrue(indexExists(jdbc, "q_backtest_experiment", "ix_q_backtest_experiment_strategy"));
    assertTrue(indexExists(jdbc, "q_backtest_experiment", "ix_q_backtest_experiment_symbol"));
    assertTrue(
        indexExists(
            jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_candidate_id"));
    assertTrue(
        indexExists(
            jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_candidate_index"));
    assertTrue(
        indexExists(
            jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_training_run"));
    assertTrue(
        indexExists(
            jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_validation_run"));
    assertTrue(
        indexExists(
            jdbc,
            "q_backtest_experiment_candidate",
            "ix_q_backtest_experiment_candidate_dispatch"));
  }

  @Test
  void realMyBatisCasBatchAndSpringRollbackContractsHold() throws Exception {
    DriverManagerDataSource dataSource = migrate();
    try (AnnotationConfigApplicationContext context = context(dataSource)) {
      BacktestExperimentMapper experiments = context.getBean(BacktestExperimentMapper.class);
      BacktestExperimentCandidateMapper candidates =
          context.getBean(BacktestExperimentCandidateMapper.class);
      BacktestRunMapper runs = context.getBean(BacktestRunMapper.class);
      TransactionTemplate transaction =
          new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));

      BacktestExperimentRow experiment = experiment(UUID.randomUUID().toString());
      assertEquals(
          1,
          transaction
              .execute(status -> Integer.valueOf(experiments.insert(experiment)))
              .intValue());
      BacktestExperimentCandidateRow first = candidate(experiment.experimentId, 0);
      BacktestExperimentCandidateRow second = candidate(experiment.experimentId, 1);
      assertEquals(
          2,
          transaction
              .execute(status -> Integer.valueOf(candidates.insertBatch(List.of(first, second))))
              .intValue());
      assertEquals(2L, candidates.count(experiment.experimentId));
      assertEquals(2, candidates.findAllByExperimentIds(List.of(experiment.experimentId)).size());

      String firstToken = UUID.randomUUID().toString();
      assertEquals(
          1,
          transaction
              .execute(
                  status ->
                      Integer.valueOf(
                          candidates.claimNextPending(
                              experiment.experimentId, firstToken, Instant.now())))
              .intValue());
      assertEquals(
          0,
          candidates.markDispatched(
              first.candidateId, UUID.randomUUID().toString(), Instant.now()));
      assertEquals(1, candidates.markDispatched(first.candidateId, firstToken, Instant.now()));

      BacktestRunRow run = run(first.trainingRunId);
      assertEquals(1, transaction.execute(status -> Integer.valueOf(runs.insert(run))).intValue());
      assertEquals(1, runs.findByRunIds(List.of(run.runId)).size());
      assertEquals(0, runs.findByRunIds(List.of()).size());

      String rollbackExperimentId = UUID.randomUUID().toString();
      BacktestExperimentRow rollbackExperiment = experiment(rollbackExperimentId);
      BacktestExperimentCandidateRow duplicateA = candidate(rollbackExperimentId, 0);
      BacktestExperimentCandidateRow duplicateB = candidate(rollbackExperimentId, 1);
      duplicateB.candidateId = duplicateA.candidateId;
      try {
        transaction.executeWithoutResult(
            status -> {
              assertEquals(1, experiments.insert(rollbackExperiment));
              candidates.insertBatch(List.of(duplicateA, duplicateB));
            });
      } catch (RuntimeException expected) {
        // The duplicate key must roll back both the candidate statement and experiment insert.
      }
      assertEquals(null, experiments.findByExperimentId(rollbackExperimentId));

      assertEquals(1, claimOneConcurrently(transaction, candidates, experiment.experimentId));
    }
  }

  private static int claimOneConcurrently(
      TransactionTemplate transaction,
      BacktestExperimentCandidateMapper candidates,
      String experimentId)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Integer> claim =
          () ->
              transaction.execute(
                  status ->
                      candidates.claimNextPending(
                          experimentId, UUID.randomUUID().toString(), Instant.now()));
      List<Future<Integer>> futures = executor.invokeAll(List.of(claim, claim));
      return futures.get(0).get() + futures.get(1).get();
    } finally {
      executor.shutdownNow();
    }
  }

  private static DriverManagerDataSource migrate() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    return dataSource;
  }

  private static AnnotationConfigApplicationContext context(DataSource dataSource) {
    MyBatisTestConfiguration.DATA_SOURCE = dataSource;
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(MyBatisTestConfiguration.class);
    context.refresh();
    return context;
  }

  @Configuration
  @MapperScan(basePackageClasses = BacktestExperimentMapper.class)
  static class MyBatisTestConfiguration {
    private static DataSource DATA_SOURCE;

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

    @Bean
    DataSourceTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }
  }

  private static BacktestExperimentRow experiment(String experimentId) {
    BacktestExperimentRow row = new BacktestExperimentRow();
    row.experimentId = experimentId;
    row.datasetId = 1;
    row.provider = "BINANCE";
    row.marketType = "SPOT";
    row.dataType = "KLINE";
    row.symbol = "BTCUSDT";
    row.intervalCode = "1m";
    row.strategyCode = "EMA_CROSS_LONG_ONLY";
    row.strategyVersion = "1.0.0";
    row.parameterGridJson = "{\"fastPeriod\":[5],\"slowPeriod\":[20]}";
    row.candidateCount = 2;
    row.trainingStartOpenTimeMs = 0;
    row.trainingEndOpenTimeMs = 60000;
    row.validationStartOpenTimeMs = 60000;
    row.validationEndOpenTimeMs = 120000;
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = new BigDecimal("0.001");
    row.forceCloseAtEnd = true;
    row.createdAt = Instant.now();
    row.updatedAt = row.createdAt;
    return row;
  }

  private static BacktestExperimentCandidateRow candidate(String experimentId, int index) {
    BacktestExperimentCandidateRow row = new BacktestExperimentCandidateRow();
    row.candidateId = UUID.randomUUID().toString();
    row.experimentId = experimentId;
    row.candidateIndex = index;
    row.parametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
    row.trainingRunId = UUID.randomUUID().toString();
    row.validationRunId = UUID.randomUUID().toString();
    row.createdAt = Instant.now();
    row.updatedAt = row.createdAt;
    return row;
  }

  private static BacktestRunRow run(String runId) {
    BacktestRunRow row = new BacktestRunRow();
    row.runId = runId;
    row.datasetId = 1;
    row.provider = "BINANCE";
    row.marketType = "SPOT";
    row.dataType = "KLINE";
    row.symbol = "BTCUSDT";
    row.intervalCode = "1m";
    row.startOpenTimeMs = 0;
    row.endOpenTimeExclusiveMs = 60000;
    row.strategyCode = "EMA_CROSS_LONG_ONLY";
    row.strategyVersion = "1.0.0";
    row.requestedParametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
    row.orderAmount = BigDecimal.ONE;
    row.feeRate = new BigDecimal("0.001");
    row.forceCloseAtEnd = true;
    row.queuedAt = Instant.now();
    row.updatedAt = row.queuedAt;
    return row;
  }

  private int tableCount(JdbcTemplate jdbc, String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
            + " table_name=?",
        Integer.class,
        table);
  }

  private String columnType(JdbcTemplate jdbc, String table, String column) {
    return jdbc.queryForObject(
        "SELECT CASE WHEN DATA_TYPE='decimal' THEN"
            + " LOWER(CONCAT(DATA_TYPE,'(',NUMERIC_PRECISION,',',NUMERIC_SCALE,')')) ELSE"
            + " UPPER(DATA_TYPE) END FROM information_schema.columns WHERE table_schema=DATABASE()"
            + " AND table_name=? AND column_name=?",
        String.class,
        table,
        column);
  }

  private boolean indexExists(JdbcTemplate jdbc, String table, String index) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND"
                + " table_name=? AND index_name=?",
            Integer.class,
            table,
            index)
        > 0;
  }
}
