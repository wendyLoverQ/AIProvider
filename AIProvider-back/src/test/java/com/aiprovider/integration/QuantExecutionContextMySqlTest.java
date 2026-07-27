package com.aiprovider.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
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
class QuantExecutionContextMySqlTest {
    private static final String PROFILE = "USDM_PERPETUAL_LONG_ONLY_1X_V1";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("aiprovider_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void v71BackfillsHistoricalRowsEnforcesNotNullAndMapsAllFields() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway flyway = flyway(dataSource);
        flyway.clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("70")
                .cleanDisabled(false)
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String runId = UUID.randomUUID().toString();
        String experimentId = UUID.randomUUID().toString();
        String studyId = UUID.randomUUID().toString();
        insertHistoricalRows(jdbc, runId, experimentId, studyId);

        flyway.migrate();

        assertContext(jdbc, "q_backtest_run", "RunId", runId);
        assertContext(jdbc, "q_backtest_experiment", "ExperimentId", experimentId);
        assertContext(jdbc, "q_walk_forward_study", "StudyId", studyId);
        assertEquals(
                "LONG",
                jdbc.queryForObject(
                        "SELECT PositionSide FROM q_backtest_trade WHERE RunId=?", String.class, runId));
        assertEquals(
                "BUY",
                jdbc.queryForObject(
                        "SELECT EntryOrderSide FROM q_backtest_trade WHERE RunId=?", String.class, runId));
        assertEquals(
                "SELL",
                jdbc.queryForObject(
                        "SELECT ExitOrderSide FROM q_backtest_trade WHERE RunId=?", String.class, runId));
        for (String table :
                List.of("q_backtest_run", "q_backtest_experiment", "q_walk_forward_study")) {
            assertEquals("NO", nullable(jdbc, table, "ExecutionProfileCode"));
            assertEquals("NO", nullable(jdbc, table, "DirectionMode"));
            assertEquals("NO", nullable(jdbc, table, "OrderSizingMode"));
        }
        assertTrue(indexExists(jdbc, "q_backtest_run", "idx_backtest_run_execution_profile"));
        assertTrue(
                indexExists(
                        jdbc,
                        "q_backtest_experiment",
                        "ix_q_backtest_experiment_execution_profile"));
        assertTrue(
                indexExists(
                        jdbc,
                        "q_walk_forward_study",
                        "ix_walk_forward_study_execution_profile"));

        try (AnnotationConfigApplicationContext context = context(dataSource)) {
            BacktestRunMapper runs = context.getBean(BacktestRunMapper.class);
            BacktestTradeMapper trades = context.getBean(BacktestTradeMapper.class);
            BacktestRunRow storedRun = runs.findByRunId(runId);
            assertEquals(PROFILE, storedRun.executionProfileCode);
            assertEquals("LONG_ONLY", storedRun.directionMode);
            assertEquals("BASE_QUANTITY", storedRun.orderSizingMode);
            BacktestTradeRow storedTrade = trades.findPage(runId, 10, 0).get(0);
            assertEquals("LONG", storedTrade.positionSide);
            assertEquals("BUY", storedTrade.entryOrderSide);
            assertEquals("SELL", storedTrade.exitOrderSide);
        }
    }

    @Test
    void v71FailsInsteadOfGuessingForHistoricalUnknownMarketType() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway flyway = flyway(dataSource);
        flyway.clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("70")
                .cleanDisabled(false)
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update(
                "INSERT INTO q_backtest_run(RunId,DatasetId,Provider,MarketType,DataType,Symbol,"
                        + "IntervalCode,StartOpenTimeMs,EndOpenTimeExclusiveMs,StrategyCode,"
                        + "StrategyVersion,RequestedParametersJson,OrderAmount,FeeRate,ForceCloseAtEnd,"
                        + "Status,ProgressPercent,QueuedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),
                1,
                "BINANCE_USDM",
                "UNKNOWN",
                "CANDLE",
                "BTCUSDT",
                "1m",
                0,
                60000,
                "EMA_CROSS_LONG_ONLY",
                "1.0.0",
                "{}",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                "QUEUED",
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now());
        assertThrows(RuntimeException.class, flyway::migrate);
    }

    private static void insertHistoricalRows(
            JdbcTemplate jdbc, String runId, String experimentId, String studyId) {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO q_backtest_run(RunId,DatasetId,Provider,MarketType,DataType,Symbol,"
                        + "IntervalCode,StartOpenTimeMs,EndOpenTimeExclusiveMs,StrategyCode,"
                        + "StrategyVersion,RequestedParametersJson,OrderAmount,FeeRate,ForceCloseAtEnd,"
                        + "Status,ProgressPercent,QueuedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                runId,
                1,
                "BINANCE_USDM",
                "USDM_PERPETUAL",
                "CANDLE",
                "BTCUSDT",
                "1m",
                0,
                60000,
                "EMA_CROSS_LONG_ONLY",
                "1.0.0",
                "{}",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                "QUEUED",
                BigDecimal.ZERO,
                now,
                now);
        jdbc.update(
                "INSERT INTO q_backtest_experiment(ExperimentId,DatasetId,Provider,MarketType,"
                        + "DataType,Symbol,IntervalCode,StrategyCode,StrategyVersion,ParameterGridJson,"
                        + "CandidateCount,TrainingStartOpenTimeMs,TrainingEndOpenTimeMs,"
                        + "ValidationStartOpenTimeMs,ValidationEndOpenTimeMs,OrderAmount,FeeRate,"
                        + "ForceCloseAtEnd,Status,CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                experimentId,
                1,
                "BINANCE_USDM",
                "USDM_PERPETUAL",
                "CANDLE",
                "BTCUSDT",
                "1m",
                "EMA_CROSS_LONG_ONLY",
                "1.0.0",
                "{\"fastPeriod\":[5],\"slowPeriod\":[20]}",
                1,
                0,
                60000,
                60000,
                120000,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                "QUEUED",
                now,
                now);
        jdbc.update(
                "INSERT INTO q_walk_forward_study(StudyId,DatasetId,Provider,MarketType,DataType,"
                        + "Symbol,IntervalCode,StrategyCode,StrategyVersion,ParameterGridJson,WindowMode,"
                        + "StudyStartOpenTimeMs,StudyEndOpenTimeMs,TrainingBars,ValidationBars,StepBars,"
                        + "FoldCount,CandidateCountPerFold,TotalChildRuns,SelectionMetric,"
                        + "MinimumTrainTrades,OrderAmount,FeeRate,ForceCloseAtEnd,Status,ProgressPercent,"
                        + "CreatedAt,UpdatedAt) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                studyId,
                1,
                "BINANCE_USDM",
                "USDM_PERPETUAL",
                "CANDLE",
                "BTCUSDT",
                "1m",
                "EMA_CROSS_LONG_ONLY",
                "1.0.0",
                "{\"fastPeriod\":[5],\"slowPeriod\":[20]}",
                "ROLLING",
                0,
                120000,
                1,
                1,
                1,
                1,
                1,
                2,
                "TRAIN_TOTAL_RETURN_RATIO",
                0,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                true,
                "QUEUED",
                BigDecimal.ZERO,
                now,
                now);
        jdbc.update(
                "INSERT INTO q_backtest_trade(RunId,TradeNo,EntrySignalIndex,EntryIndex,EntryTimeMs,"
                        + "EntryPrice,ExitSignalIndex,ExitIndex,ExitTimeMs,ExitPrice,Amount,GrossProfit,"
                        + "Fee,NetProfit,ReturnRatio,BarsHeld,ForcedExit,ExitReason)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                runId,
                1,
                0,
                1,
                60000,
                BigDecimal.ONE,
                1,
                2,
                120000,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                1,
                false,
                "STRATEGY_EXIT");
    }

    private static void assertContext(
            JdbcTemplate jdbc, String table, String idColumn, String idValue) {
        assertEquals(
                PROFILE,
                jdbc.queryForObject(
                        "SELECT ExecutionProfileCode FROM " + table + " WHERE " + idColumn + "=?",
                        String.class,
                        idValue));
        assertEquals(
                "LONG_ONLY",
                jdbc.queryForObject(
                        "SELECT DirectionMode FROM " + table + " WHERE " + idColumn + "=?",
                        String.class,
                        idValue));
        assertEquals(
                "BASE_QUANTITY",
                jdbc.queryForObject(
                        "SELECT OrderSizingMode FROM " + table + " WHERE " + idColumn + "=?",
                        String.class,
                        idValue));
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    private static AnnotationConfigApplicationContext context(DataSource dataSource) {
        MyBatisConfiguration.DATA_SOURCE = dataSource;
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MyBatisConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration
    @MapperScan(basePackageClasses = BacktestRunMapper.class)
    static class MyBatisConfiguration {
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
    }

    private static String nullable(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE()"
                        + " AND table_name=? AND column_name=?",
                String.class,
                table,
                column);
    }

    private static boolean indexExists(JdbcTemplate jdbc, String table, String index) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()"
                                + " AND table_name=? AND index_name=?",
                        Integer.class,
                        table,
                        index)
                > 0;
    }
}
