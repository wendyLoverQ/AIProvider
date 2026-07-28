package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class QuantResearchStudyMySqlTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
      .withDatabaseName("aiprovider_research_test").withUsername("test").withPassword("test");

  @Test void migrationsCreateResearchAndNullableCapitalModelContracts() {
    DataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='q_research_study'", Integer.class));
    assertEquals("json", columnType(jdbc, "ParameterSpaceJson"));
    assertEquals("decimal(38,18)", columnType(jdbc, "OrderAmount"));
    assertEquals("ascii_bin", jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='q_research_study' AND column_name='ComparisonGroupKey'", String.class));
    assertEquals("YES", jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='q_walk_forward_study' AND column_name='OosTotalReturnRatio'", String.class));
    assertTrue(indexExists(jdbc, "uk_research_study_child"));
    assertTrue(indexExists(jdbc, "ix_research_study_status_updated"));
    assertEquals("smallint", jdbc.queryForObject("SELECT LOWER(DATA_TYPE) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='q_walk_forward_study' AND column_name='OosAggregateVersion'", String.class));
    assertTrue(indexExistsOn(jdbc, "q_walk_forward_study", "ix_walk_forward_oos_recovery"));
    assertNullableDecimal(jdbc, "q_backtest_run", "InitialCapital");
    assertNullableDecimal(jdbc, "q_backtest_run", "FinalEquity");
    assertNullableDecimal(jdbc, "q_backtest_equity", "EquityValue");
    assertNullableDecimal(jdbc, "q_backtest_equity", "ExposureRatio");
    assertNullableDecimal(jdbc, "q_backtest_experiment", "InitialCapital");
    assertNullableDecimal(jdbc, "q_walk_forward_study", "InitialCapital");
    assertNullableDecimal(jdbc, "q_research_study", "InitialCapital");
    String id = "00000000-0000-0000-0000-000000000001";
    jdbc.update("INSERT INTO q_research_study(ResearchStudyId,Name,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,EvaluationMode,ParameterSpaceMode,ParameterSpaceJson,ExpandedParameterGridJson,CandidateCount,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,ComparisonGroupKey,WalkForwardStudyId,Status,CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
        id, "test", 1, "BINANCE", "USDM_PERPETUAL", "CANDLE", "BTCUSDT", "1h", "EMA", "1", "PROFILE", "LONG_ONLY", "BASE_QUANTITY", "WALK_FORWARD", "STRATEGY_DEFAULT", "{}", "{}", 1, 1, 2, 1, 1, "METRIC", 0, new BigDecimal("0.01"), new BigDecimal("0.0004"), true, "a".repeat(64), "00000000-0000-0000-0000-000000000002", "QUEUED");
    assertThrows(Exception.class, () -> jdbc.update("INSERT INTO q_research_study(ResearchStudyId,Name,DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,EvaluationMode,ParameterSpaceMode,ParameterSpaceJson,ExpandedParameterGridJson,CandidateCount,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,ComparisonGroupKey,WalkForwardStudyId,Status,CreatedAt,UpdatedAt) SELECT ResearchStudyId,'second',DatasetId,Provider,MarketType,DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ExecutionProfileCode,DirectionMode,OrderSizingMode,EvaluationMode,ParameterSpaceMode,ParameterSpaceJson,ExpandedParameterGridJson,CandidateCount,StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,SelectionMetric,MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,ComparisonGroupKey,WalkForwardStudyId,Status,CreatedAt,UpdatedAt FROM q_research_study WHERE ResearchStudyId=?", id));
  }

  private String columnType(JdbcTemplate jdbc, String column) { return jdbc.queryForObject("SELECT LOWER(COLUMN_TYPE) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='q_research_study' AND column_name=?", String.class, column); }
  private boolean indexExists(JdbcTemplate jdbc, String index) { return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='q_research_study' AND index_name=?", Integer.class, index) > 0; }
  private boolean indexExistsOn(JdbcTemplate jdbc, String table, String index) { return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?", Integer.class, table, index) > 0; }
  private void assertNullableDecimal(JdbcTemplate jdbc, String table, String column) {
    assertEquals("decimal(38,18)", jdbc.queryForObject("SELECT LOWER(COLUMN_TYPE) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", String.class, table, column));
    assertEquals("YES", jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", String.class, table, column));
  }
}
