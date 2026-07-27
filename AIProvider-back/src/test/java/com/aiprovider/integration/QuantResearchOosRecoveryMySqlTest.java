package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.config.quant.QuantExperimentProperties;
import com.aiprovider.controller.quant.dto.WalkForwardStudyDtos;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.aiprovider.service.quant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class QuantResearchOosRecoveryMySqlTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
      .withDatabaseName("aiprovider_research_oos_test").withUsername("test").withPassword("test");

  @Test void realRecoveryWritesGlobalOosAndResearchReadsIt() {
    try (TestContext context = open()) {
      clear(context.jdbc);
      QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String studyId = UUID.randomUUID().toString();
      WalkForwardStudyRow study = study(studyId, 2, "COMPLETED");
      fixture.insertTerminalWalkForwardStudy(studyId, 2, "COMPLETED");
      fixture.insertTerminalFold(studyId, 0, "COMPLETED", "v1", "{\"fastPeriod\":5}");
      fixture.insertTerminalFold(studyId, 1, "COMPLETED", "v2", "{\"fastPeriod\":7}");
      fixture.insertCompletedBacktestRun("v1", 2, "0.10", "0.10");
      fixture.insertCompletedBacktestRun("v2", 3, "0.20", "0.20");
      fixture.insertBacktestEquity("v1", List.of("1.0", "2.5", "2.0"), 0);
      fixture.insertBacktestEquity("v2", List.of("1.0", "0.9"), 3);

      ObjectMapper json = new ObjectMapper();
      BacktestExperimentService experiments = new BacktestExperimentService(context.experiments, context.candidates,
          context.runs, null, null, null, json, new QuantExperimentProperties());
      WalkForwardStudySnapshotLoader loader = new WalkForwardStudySnapshotLoader(context.folds, experiments, context.runs, context.equity);
      WalkForwardOosCalculator calculator = new WalkForwardOosCalculator(json);
      WalkForwardOosRecoveryService recovery = new WalkForwardOosRecoveryService(context.studies, context.folds, loader, calculator);
      recovery.recoverBatch(20);

      assertEquals(2, scalar(context.jdbc, "SuccessfulOosFolds", studyId));
      assertEquals(0, scalar(context.jdbc, "FailedFolds", studyId));
      assertEquals(0, scalar(context.jdbc, "HasOosGaps", studyId));
      assertEquals(5, scalar(context.jdbc, "OosTradeCount", studyId));
      assertEquals(1, scalar(context.jdbc, "ParameterChanges", studyId));
      assertEquals(1, scalar(context.jdbc, "OosAggregateVersion", studyId));
      BigDecimal drawdown = decimal(context.jdbc, "OosMaximumDrawdownRatio", studyId);
      assertEquals(18, drawdown.scale());
      assertEquals(0, drawdown.compareTo(new BigDecimal("0.280000000000000000")));

      WalkForwardStudyService walkForward = new WalkForwardStudyService(context.studies, context.folds, loader, json, calculator);
      WalkForwardStudyDtos.OosEquity equity = walkForward.oosEquity(studyId, 100);
      assertEquals(0, equity.maximumDrawdownRatio().compareTo(drawdown));
      assertEquals(18, equity.maximumDrawdownRatio().scale());

      String researchId = UUID.randomUUID().toString();
      fixture.insertResearchStudySnapshot(researchId, studyId, "COMPLETED", "a".repeat(64));
      ResearchStudyService researchService = new ResearchStudyService(context.research, json);
      assertEquals(0, researchService.get(researchId).summary().oosMaximumDrawdownRatio().compareTo(drawdown));
      assertEquals(18, researchService.get(researchId).summary().oosMaximumDrawdownRatio().scale());
    }
  }

  @Test void realRecoveryPersistsCompletedWithFailuresAndFailedWithoutSuccess() {
    try (TestContext context = open()) {
      clear(context.jdbc); QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String mixed = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(mixed, 2, "COMPLETED_WITH_FAILURES");
      fixture.insertTerminalFold(mixed, 0, "COMPLETED", "mv", "{\"fastPeriod\":5}"); fixture.insertTerminalFold(mixed, 1, "FAILED", "missing", null);
      fixture.insertCompletedBacktestRun("mv", 4, "0.25", "0.04"); fixture.insertBacktestEquity("mv", List.of("1.0", "1.25"), 0);
      String failed = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(failed, 2, "FAILED");
      fixture.insertTerminalFold(failed, 0, "FAILED", "missing-0", null); fixture.insertTerminalFold(failed, 1, "FAILED", "missing-1", null);
      new WalkForwardOosRecoveryService(context.studies, context.folds, loader(context), new WalkForwardOosCalculator(new ObjectMapper())).recoverBatch(20);
      assertEquals(Integer.valueOf(1), scalar(context.jdbc, "SuccessfulOosFolds", mixed)); assertEquals(Integer.valueOf(1), scalar(context.jdbc, "FailedFolds", mixed));
      assertEquals(Integer.valueOf(1), scalar(context.jdbc, "OosAggregateVersion", mixed)); assertEquals(Integer.valueOf(1), scalar(context.jdbc, "OosAggregateVersion", failed));
      assertNull(decimal(context.jdbc, "OosTotalReturnRatio", failed)); assertNull(decimal(context.jdbc, "OosMaximumDrawdownRatio", failed));
      String researchId = UUID.randomUUID().toString(); fixture.insertResearchStudySnapshot(researchId, failed, "FAILED", "b".repeat(64));
      assertEquals(Integer.valueOf(0), new ResearchStudyService(context.research, new ObjectMapper()).get(researchId).summary().successfulOosFolds());
    }
  }

  private WalkForwardStudySnapshotLoader loader(TestContext context) {
    ObjectMapper json = new ObjectMapper();
    BacktestExperimentService experiments = new BacktestExperimentService(context.experiments, context.candidates, context.runs, null, null, null, json, new QuantExperimentProperties());
    return new WalkForwardStudySnapshotLoader(context.folds, experiments, context.runs, context.equity);
  }

  private static TestContext open() {
    DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    TestConfiguration.DATA_SOURCE = dataSource;
    AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext(TestConfiguration.class);
    return new TestContext(app, dataSource);
  }

  private static void clear(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM q_research_study"); jdbc.update("DELETE FROM q_walk_forward_fold");
    jdbc.update("DELETE FROM q_walk_forward_study"); jdbc.update("DELETE FROM q_backtest_equity");
    jdbc.update("DELETE FROM q_backtest_trade");
    jdbc.update("DELETE FROM q_backtest_run"); jdbc.update("DELETE FROM q_backtest_experiment_candidate");
    jdbc.update("DELETE FROM q_backtest_experiment");
  }

  private static WalkForwardStudyRow study(String id, int foldCount, String status) {
    WalkForwardStudyRow row = new WalkForwardStudyRow(); row.studyId = id; row.datasetId = 1; row.provider = "BINANCE_USDM";
    row.marketType = "USDM_PERPETUAL"; row.dataType = "CANDLE"; row.symbol = "BTCUSDT"; row.intervalCode = "1m";
    row.strategyCode = "EMA_CROSS_LONG_ONLY"; row.strategyVersion = "1.0.0"; row.executionProfileCode = "PROFILE";
    row.directionMode = "LONG_ONLY"; row.orderSizingMode = "BASE_QUANTITY"; row.parameterGridJson = "{\"fastPeriod\":[5,7]}";
    row.windowMode = "ROLLING"; row.studyStartOpenTimeMs = 0; row.studyEndOpenTimeMs = 10000; row.trainingBars = 1;
    row.validationBars = 1; row.stepBars = 1; row.foldCount = foldCount; row.candidateCountPerFold = 1; row.totalChildRuns = 2;
    row.selectionMetric = "TRAIN_TOTAL_RETURN_RATIO"; row.minimumTrainTrades = 0; row.orderAmount = BigDecimal.ONE;
    row.feeRate = new BigDecimal("0.001"); row.forceCloseAtEnd = true; row.status = status; row.progressPercent = BigDecimal.valueOf(100);
    row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH; row.startedAt = Instant.EPOCH; row.finishedAt = Instant.EPOCH; return row;
  }

  private static WalkForwardFoldRow fold(String studyId, int index, String status, String validation, String parameters) {
    WalkForwardFoldRow row = new WalkForwardFoldRow(); row.foldId = UUID.randomUUID().toString(); row.studyId = studyId; row.foldIndex = index;
    row.trainingStartOpenTimeMs = index * 1000L; row.trainingEndOpenTimeMs = index * 1000L + 500;
    row.validationStartOpenTimeMs = row.trainingEndOpenTimeMs; row.validationEndOpenTimeMs = row.trainingEndOpenTimeMs + 500;
    row.experimentId = UUID.randomUUID().toString(); row.status = status; row.selectedCandidateId = "candidate-" + index;
    row.selectedParametersJson = parameters; row.selectedTrainingRunId = "t" + index; row.selectedValidationRunId = validation;
    row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH; row.startedAt = Instant.EPOCH; row.finishedAt = Instant.EPOCH; return row;
  }

  private static void insertRun(BacktestRunMapper runs, String id, int trades, String returns, String fees) {
    BacktestRunRow row = new BacktestRunRow(); row.runId = id; row.datasetId = 1; row.provider = "BINANCE_USDM";
    row.marketType = "USDM_PERPETUAL"; row.dataType = "CANDLE"; row.symbol = "BTCUSDT"; row.intervalCode = "1m";
    row.startOpenTimeMs = 0; row.endOpenTimeExclusiveMs = 10000; row.strategyCode = "EMA_CROSS_LONG_ONLY"; row.strategyVersion = "1.0.0";
    row.executionProfileCode = "PROFILE"; row.directionMode = "LONG_ONLY"; row.orderSizingMode = "BASE_QUANTITY";
    row.requestedParametersJson = "{}"; row.orderAmount = BigDecimal.ONE; row.feeRate = new BigDecimal("0.001"); row.forceCloseAtEnd = true;
    row.status = "COMPLETED"; row.progressPercent = BigDecimal.valueOf(100); row.tradeCount = trades; row.totalReturnRatio = new BigDecimal(returns);
    row.maximumDrawdownRatio = new BigDecimal("0.1"); row.totalFees = new BigDecimal(fees); row.queuedAt = Instant.EPOCH; row.finishedAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH;
    assertEquals(1, runs.insert(row));
  }

  private static void insertEquity(BacktestEquityMapper equity, String runId, List<String> values, long offset) {
    List<BacktestEquityRow> rows = new java.util.ArrayList<>();
    for (int i = 0; i < values.size(); i++) { BacktestEquityRow row = new BacktestEquityRow(); row.runId = runId; row.pointIndex = i; row.openTimeMs = offset + i; row.equityRatio = new BigDecimal(values.get(i)); row.drawdownRatio = BigDecimal.ZERO; rows.add(row); }
    assertEquals(values.size(), equity.insertBatch(rows));
  }

  private static ResearchStudyRow research(String id, String child) {
    ResearchStudyRow row = new ResearchStudyRow(); row.researchStudyId = id; row.name = "research"; row.datasetId = 1;
    row.provider = "BINANCE_USDM"; row.marketType = "USDM_PERPETUAL"; row.dataType = "CANDLE"; row.symbol = "BTCUSDT"; row.intervalCode = "1m";
    row.strategyCode = "EMA_CROSS_LONG_ONLY"; row.strategyVersion = "1.0.0"; row.executionProfileCode = "PROFILE";
    row.directionMode = "LONG_ONLY"; row.orderSizingMode = "BASE_QUANTITY"; row.evaluationMode = "WALK_FORWARD"; row.parameterSpaceMode = "STRATEGY_DEFAULT";
    row.parameterSpaceJson = "{\"fastPeriod\":{\"min\":5,\"max\":7,\"step\":2}}"; row.expandedParameterGridJson = "{\"fastPeriod\":[5,7]}";
    row.candidateCount = 2; row.studyStartOpenTimeMs = 0; row.studyEndOpenTimeMs = 10000; row.trainingBars = 1; row.validationBars = 1;
    row.selectionMetric = "TRAIN_TOTAL_RETURN_RATIO"; row.minimumTrainTrades = 0; row.orderAmount = BigDecimal.ONE; row.feeRate = new BigDecimal("0.001");
    row.forceCloseAtEnd = true; row.comparisonGroupKey = "a".repeat(64); row.walkForwardStudyId = child; row.status = "COMPLETED"; row.progressPercent = BigDecimal.valueOf(100);
    row.createdAt = Instant.EPOCH; row.updatedAt = Instant.EPOCH; row.startedAt = Instant.EPOCH; row.finishedAt = Instant.EPOCH; return row;
  }

  private static Integer scalar(JdbcTemplate jdbc, String column, String id) { return jdbc.queryForObject("SELECT " + column + " FROM q_walk_forward_study WHERE StudyId=?", Integer.class, id); }
  private static BigDecimal decimal(JdbcTemplate jdbc, String column, String id) { return jdbc.queryForObject("SELECT " + column + " FROM q_walk_forward_study WHERE StudyId=?", BigDecimal.class, id); }

  private record TestContext(AnnotationConfigApplicationContext app, JdbcTemplate jdbc, WalkForwardStudyMapper studies,
      WalkForwardFoldMapper folds, BacktestExperimentMapper experiments, BacktestExperimentCandidateMapper candidates,
      BacktestRunMapper runs, BacktestEquityMapper equity, ResearchStudyMapper research) implements AutoCloseable {
    TestContext(AnnotationConfigApplicationContext app, DataSource ds) { this(app, new JdbcTemplate(ds), app.getBean(WalkForwardStudyMapper.class), app.getBean(WalkForwardFoldMapper.class), app.getBean(BacktestExperimentMapper.class), app.getBean(BacktestExperimentCandidateMapper.class), app.getBean(BacktestRunMapper.class), app.getBean(BacktestEquityMapper.class), app.getBean(ResearchStudyMapper.class)); }
    public void close() { app.close(); }
  }

  @Configuration
  @MapperScan(basePackages = "com.aiprovider.mapper")
  static class TestConfiguration {
    static DataSource DATA_SOURCE;
    @Bean DataSource dataSource() { return DATA_SOURCE; }
    @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception { SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.afterPropertiesSet(); return factory.getObject(); }
  }
}
