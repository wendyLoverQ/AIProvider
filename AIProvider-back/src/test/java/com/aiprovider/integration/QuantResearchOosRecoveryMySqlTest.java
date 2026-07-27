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
import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import com.aiprovider.quant.market.history.model.MarketDataset;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.StrategyRegistry;

@Testcontainers(disabledWithoutDocker = true)
class QuantResearchOosRecoveryMySqlTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
      .withDatabaseName("aiprovider_research_oos_test").withUsername("test").withPassword("test");

  @Test void realRecoveryWritesGlobalOosAndResearchReadsIt() {
    try (TestContext context = open()) {
      clear(context.jdbc);
      QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String studyId = UUID.randomUUID().toString();
      fixture.insertTerminalWalkForwardStudy(studyId, 2, "COMPLETED");
      fixture.insertTerminalFold(studyId, 0, "COMPLETED", "v1", "{\"fastPeriod\":5}");
      fixture.insertTerminalFold(studyId, 1, "COMPLETED", "v2", "{\"fastPeriod\":7}");
      fixture.insertCompletedBacktestRun("v1", 2, "0.10", "0.10");
      fixture.insertCompletedBacktestRun("v2", 3, "0.20", "0.20");
      fixture.insertBacktestEquity("v1", List.of("1.0", "2.5", "2.0"), 0);
      fixture.insertBacktestEquity("v2", List.of("1.0", "0.9"), 3);

      ObjectMapper json = new ObjectMapper();
      BacktestExperimentService experiments = experimentService(context, json);
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

  @Test void rejectsIncompleteFoldCoverageWithoutBlockingLaterStudies() {
    try (TestContext context = open()) {
      clear(context.jdbc); QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String broken = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(broken, 3, "COMPLETED");
      fixture.insertTerminalFold(broken, 0, "COMPLETED", "broken-v", "{\"fastPeriod\":5}");
      fixture.insertTerminalFold(broken, 2, "COMPLETED", "broken-v2", "{\"fastPeriod\":7}");
      String healthy = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(healthy, 1, "COMPLETED");
      fixture.insertTerminalFold(healthy, 0, "COMPLETED", "healthy-v", "{\"fastPeriod\":5}");
      fixture.insertCompletedBacktestRun("healthy-v", 2, "0.10", "0.01"); fixture.insertBacktestEquity("healthy-v", List.of("1.0", "1.1"), 0);
      new WalkForwardOosRecoveryService(context.studies, context.folds, loader(context), new WalkForwardOosCalculator(new ObjectMapper())).recoverBatch(20);
      assertNull(scalar(context.jdbc, "OosAggregateVersion", broken)); assertNull(scalar(context.jdbc, "SuccessfulOosFolds", broken));
      assertEquals(Integer.valueOf(1), scalar(context.jdbc, "OosAggregateVersion", healthy));
    }
  }

  @Test void rejectsTerminalStudyContainingNonTerminalFold() {
    try (TestContext context = open()) {
      clear(context.jdbc); QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String broken = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(broken, 2, "COMPLETED");
      fixture.insertTerminalFold(broken, 0, "COMPLETED", "waiting-v", "{\"fastPeriod\":5}");
      fixture.insertTerminalFold(broken, 1, "WAITING_EXPERIMENT", "waiting-v2", "{\"fastPeriod\":7}");
      String healthy = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(healthy, 1, "COMPLETED");
      fixture.insertTerminalFold(healthy, 0, "COMPLETED", "healthy-v", "{\"fastPeriod\":5}");
      fixture.insertCompletedBacktestRun("healthy-v", 2, "0.10", "0.01"); fixture.insertBacktestEquity("healthy-v", List.of("1.0", "1.1"), 0);
      new WalkForwardOosRecoveryService(context.studies, context.folds, loader(context), new WalkForwardOosCalculator(new ObjectMapper())).recoverBatch(20);
      assertNull(scalar(context.jdbc, "OosAggregateVersion", broken)); assertEquals(Integer.valueOf(1), scalar(context.jdbc, "OosAggregateVersion", healthy));
    }
  }

  @Test void doesNotRewriteNormalizedDecimalResults() {
    try (TestContext context = open()) {
      clear(context.jdbc); QuantResearchMySqlFixture fixture = new QuantResearchMySqlFixture(context.jdbc);
      String studyId = UUID.randomUUID().toString(); fixture.insertTerminalWalkForwardStudy(studyId, 1, "COMPLETED");
      fixture.insertTerminalFold(studyId, 0, "COMPLETED", "precise-v", "{\"fastPeriod\":5}");
      fixture.insertCompletedBacktestRun("precise-v", 2, "0.10", "0.01");
      fixture.insertBacktestEquity("precise-v", List.of("1.123456789012345678901", "0.987654321098765432109"), 0);
      ObjectMapper json = new ObjectMapper(); WalkForwardStudySnapshotLoader loader = loader(context);
      new WalkForwardOosRecoveryService(context.studies, context.folds, loader, new WalkForwardOosCalculator(json)).recoverBatch(20);
      Instant updatedAt = context.jdbc.queryForObject("SELECT UpdatedAt FROM q_walk_forward_study WHERE StudyId=?", Instant.class, studyId);
      BigDecimal totalReturn = decimal(context.jdbc, "OosTotalReturnRatio", studyId);
      WalkForwardStudyService service = new WalkForwardStudyService(context.studies, context.folds, loader, json, new WalkForwardOosCalculator(json));
      service.get(studyId); service.page(1, 10, null, null, null); service.oosEquity(studyId, 100);
      assertEquals(updatedAt, context.jdbc.queryForObject("SELECT UpdatedAt FROM q_walk_forward_study WHERE StudyId=?", Instant.class, studyId));
      assertEquals(18, totalReturn.scale()); assertEquals(Integer.valueOf(1), scalar(context.jdbc, "OosAggregateVersion", studyId));
    }
  }

  private WalkForwardStudySnapshotLoader loader(TestContext context) {
    ObjectMapper json = new ObjectMapper();
    BacktestExperimentService experiments = experimentService(context, json);
    return new WalkForwardStudySnapshotLoader(context.folds, experiments, context.runs, context.equity);
  }

  private BacktestExperimentService experimentService(TestContext context, ObjectMapper json) {
    return new BacktestExperimentService(context.experiments, context.candidates, context.runs,
        new EmptyMarketDatasetRepository(), new StrategyRegistry(), new BacktestCompatibilityService(new ExecutionProfileRegistry()),
        json, new QuantExperimentProperties());
  }

  private static final class EmptyMarketDatasetRepository implements MarketDatasetRepository {
    public MarketDataset findByKey(MarketProviderId provider, MarketType marketType, String dataType, String symbol, String intervalCode) { return null; }
    public MarketDataset findById(long datasetId) { return null; }
    public List<MarketDataset> findPage(MarketProviderId provider, String symbol, String intervalCode, String status, int limit, int offset) { return List.of(); }
    public long insert(MarketDataset dataset) { throw new UnsupportedOperationException("creation is not part of this recovery fixture"); }
    public int updateStats(MarketDataset dataset) { throw new UnsupportedOperationException("creation is not part of this recovery fixture"); }
    public int updateLastSync(long datasetId, String lastSyncTaskId, Instant lastSuccessfulSyncAt) { throw new UnsupportedOperationException("creation is not part of this recovery fixture"); }
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
