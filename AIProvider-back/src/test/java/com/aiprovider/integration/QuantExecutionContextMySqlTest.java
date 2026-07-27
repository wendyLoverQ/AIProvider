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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class QuantExecutionContextMySqlTest {
    private static final String PROFILE = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
    private static MySQLContainer<?> mysql;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void startMySql8() {
        String externalUrl = System.getProperty("quant.test.mysql.jdbcUrl");
        if (externalUrl != null && !externalUrl.isBlank()) {
            jdbcUrl = externalUrl;
            username = System.getProperty("quant.test.mysql.username", "root");
            password = System.getProperty("quant.test.mysql.password", "");
            return;
        }
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker or quant.test.mysql.jdbcUrl is required for MySQL 8 execution");
        mysql =
                new MySQLContainer<>("mysql:8.0.36")
                        .withDatabaseName("aiprovider_test")
                        .withUsername("test")
                        .withPassword("test");
        mysql.start();
        jdbcUrl = mysql.getJdbcUrl();
        username = mysql.getUsername();
        password = mysql.getPassword();
    }

    @AfterAll
    static void stopMySql8() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void v71BackfillsHistoricalRowsEnforcesNotNullAndMapsAllFields() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway flyway = flyway(dataSource);
        flyway.clean();
        bootstrapLegacySchema(dataSource);
        migrateLegacySchemaToV70(dataSource);
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
            BacktestExperimentMapper experiments = context.getBean(BacktestExperimentMapper.class);
            WalkForwardStudyMapper studies = context.getBean(WalkForwardStudyMapper.class);
            BacktestTradeMapper trades = context.getBean(BacktestTradeMapper.class);
            BacktestRunRow storedRun = runs.findByRunId(runId);
            assertRowContext(storedRun.executionProfileCode, storedRun.directionMode, storedRun.orderSizingMode);
            BacktestExperimentRow storedExperiment =
                    experiments.findByExperimentId(experimentId);
            assertRowContext(
                    storedExperiment.executionProfileCode,
                    storedExperiment.directionMode,
                    storedExperiment.orderSizingMode);
            WalkForwardStudyRow storedStudy = studies.findByStudyId(studyId);
            assertRowContext(
                    storedStudy.executionProfileCode,
                    storedStudy.directionMode,
                    storedStudy.orderSizingMode);
            BacktestTradeRow storedTrade = trades.findPage(runId, 10, 0).get(0);
            assertEquals("LONG", storedTrade.positionSide);
            assertEquals("BUY", storedTrade.entryOrderSide);
            assertEquals("SELL", storedTrade.exitOrderSide);
            assertMapperWritesAndReadsAllExecutionFields(runs, experiments, studies, trades);
        }
    }

    @Test
    void v71FailsInsteadOfGuessingForHistoricalUnknownMarketType() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway flyway = flyway(dataSource);
        flyway.clean();
        bootstrapLegacySchema(dataSource);
        migrateLegacySchemaToV70(dataSource);
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

    private static void bootstrapLegacySchema(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table :
                List.of(
                        "TimerRecords",
                        "ChatMessages",
                        "LlmChatConversations",
                        "LlmChatMessages",
                        "DesktopContextSnapshots",
                        "ProactiveBroadcastTriggerLogs",
                        "NotebookNotes",
                        "Reminders")) {
            jdbc.execute(
                    "CREATE TABLE " + table
                            + " (Id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB"
                            + " DEFAULT CHARSET=utf8mb4");
        }
        jdbc.execute(
                "CREATE TABLE MaidStates (Id BIGINT NOT NULL PRIMARY KEY,"
                        + " MaidId VARCHAR(128) NOT NULL, InteractionCount INT NOT NULL DEFAULT 0)"
                        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute(
                "CREATE TABLE LlmCallLogs (Id BIGINT NOT NULL PRIMARY KEY,"
                        + " CreatedAt VARCHAR(64) NULL, ResponseStatusCode INT NULL,"
                        + " Provider VARCHAR(128) NULL, Model VARCHAR(256) NULL)"
                        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute(
                "CREATE TABLE AppRuntimeStates (Id BIGINT NOT NULL PRIMARY KEY,"
                        + " LastRole VARCHAR(128) NULL, UpdatedAt DATETIME(6) NULL)"
                        + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute(
                "CREATE TABLE AgentToolCalls (Id BIGINT NOT NULL PRIMARY KEY,"
                        + " ParentToolCallId BIGINT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        for (String table :
                List.of(
                        "VoiceRoleAudioCaches",
                        "VoiceRoleBindings",
                        "VoiceTriggerLogs")) {
            jdbc.execute(
                    "CREATE TABLE " + table
                            + " (Id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB"
                            + " DEFAULT CHARSET=utf8mb4");
        }
        jdbc.execute(
                "CREATE TABLE AppSettings (Id BIGINT NOT NULL PRIMARY KEY,"
                        + " `Key` VARCHAR(128) NOT NULL, `Value` LONGTEXT NULL)"
                + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private static void migrateLegacySchemaToV70(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target("58")
                .cleanDisabled(false)
                .load()
                .migrate();
        new JdbcTemplate(dataSource).update("DELETE FROM c_PromptOptions");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("70")
                .cleanDisabled(false)
                .load()
                .migrate();
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

    private static void assertMapperWritesAndReadsAllExecutionFields(
            BacktestRunMapper runs,
            BacktestExperimentMapper experiments,
            WalkForwardStudyMapper studies,
            BacktestTradeMapper trades) {
        Instant now = Instant.now();
        String runId = UUID.randomUUID().toString();
        BacktestRunRow run = new BacktestRunRow();
        run.runId = runId;
        run.datasetId = 2;
        run.provider = "BINANCE_USDM";
        run.marketType = "USDM_PERPETUAL";
        run.dataType = "CANDLE";
        run.symbol = "ETHUSDT";
        run.intervalCode = "1m";
        run.startOpenTimeMs = 0;
        run.endOpenTimeExclusiveMs = 60_000;
        run.strategyCode = "EMA_CROSS_LONG_ONLY";
        run.strategyVersion = "1.0.0";
        setContext(run);
        run.requestedParametersJson = "{}";
        run.orderAmount = BigDecimal.ONE;
        run.feeRate = BigDecimal.ZERO;
        run.forceCloseAtEnd = true;
        run.queuedAt = now;
        run.updatedAt = now;
        assertEquals(1, runs.insert(run));
        BacktestRunRow readRun = runs.findByRunId(runId);
        assertRowContext(
                readRun.executionProfileCode, readRun.directionMode, readRun.orderSizingMode);

        String experimentId = UUID.randomUUID().toString();
        BacktestExperimentRow experiment = new BacktestExperimentRow();
        experiment.experimentId = experimentId;
        experiment.datasetId = 2;
        experiment.provider = "BINANCE_USDM";
        experiment.marketType = "USDM_PERPETUAL";
        experiment.dataType = "CANDLE";
        experiment.symbol = "ETHUSDT";
        experiment.intervalCode = "1m";
        experiment.strategyCode = "EMA_CROSS_LONG_ONLY";
        experiment.strategyVersion = "1.0.0";
        setContext(experiment);
        experiment.parameterGridJson = "{}";
        experiment.candidateCount = 1;
        experiment.trainingStartOpenTimeMs = 0;
        experiment.trainingEndOpenTimeMs = 60_000;
        experiment.validationStartOpenTimeMs = 60_000;
        experiment.validationEndOpenTimeMs = 120_000;
        experiment.orderAmount = BigDecimal.ONE;
        experiment.feeRate = BigDecimal.ZERO;
        experiment.forceCloseAtEnd = true;
        experiment.createdAt = now;
        experiment.updatedAt = now;
        assertEquals(1, experiments.insert(experiment));
        BacktestExperimentRow readExperiment =
                experiments.findByExperimentId(experimentId);
        assertRowContext(
                readExperiment.executionProfileCode,
                readExperiment.directionMode,
                readExperiment.orderSizingMode);

        String studyId = UUID.randomUUID().toString();
        WalkForwardStudyRow study = new WalkForwardStudyRow();
        study.studyId = studyId;
        study.datasetId = 2;
        study.provider = "BINANCE_USDM";
        study.marketType = "USDM_PERPETUAL";
        study.dataType = "CANDLE";
        study.symbol = "ETHUSDT";
        study.intervalCode = "1m";
        study.strategyCode = "EMA_CROSS_LONG_ONLY";
        study.strategyVersion = "1.0.0";
        setContext(study);
        study.parameterGridJson = "{}";
        study.windowMode = "ROLLING";
        study.studyStartOpenTimeMs = 0;
        study.studyEndOpenTimeMs = 120_000;
        study.trainingBars = 1;
        study.validationBars = 1;
        study.stepBars = 1;
        study.foldCount = 1;
        study.candidateCountPerFold = 1;
        study.totalChildRuns = 2;
        study.selectionMetric = "TRAIN_TOTAL_RETURN_RATIO";
        study.orderAmount = BigDecimal.ONE;
        study.feeRate = BigDecimal.ZERO;
        study.forceCloseAtEnd = true;
        study.createdAt = now;
        study.updatedAt = now;
        assertEquals(1, studies.insert(study));
        WalkForwardStudyRow readStudy = studies.findByStudyId(studyId);
        assertRowContext(
                readStudy.executionProfileCode,
                readStudy.directionMode,
                readStudy.orderSizingMode);

        BacktestTradeRow trade = new BacktestTradeRow();
        trade.runId = runId;
        trade.tradeNo = 1;
        trade.entrySignalIndex = 0;
        trade.entryIndex = 1;
        trade.entryTimeMs = 0;
        trade.entryPrice = BigDecimal.ONE;
        trade.exitSignalIndex = 1;
        trade.exitIndex = 2;
        trade.exitTimeMs = 60_000;
        trade.exitPrice = BigDecimal.ONE;
        trade.amount = BigDecimal.ONE;
        trade.grossProfit = BigDecimal.ZERO;
        trade.fee = BigDecimal.ZERO;
        trade.netProfit = BigDecimal.ZERO;
        trade.returnRatio = BigDecimal.ZERO;
        trade.barsHeld = 1;
        trade.exitReason = "STRATEGY_EXIT";
        trade.positionSide = "LONG";
        trade.entryOrderSide = "BUY";
        trade.exitOrderSide = "SELL";
        assertEquals(1, trades.insertBatch(List.of(trade)));
        BacktestTradeRow readTrade = trades.findPage(runId, 10, 0).get(0);
        assertEquals("LONG", readTrade.positionSide);
        assertEquals("BUY", readTrade.entryOrderSide);
        assertEquals("SELL", readTrade.exitOrderSide);
    }

    private static void setContext(BacktestRunRow row) {
        row.executionProfileCode = PROFILE;
        row.directionMode = "LONG_ONLY";
        row.orderSizingMode = "BASE_QUANTITY";
    }

    private static void setContext(BacktestExperimentRow row) {
        row.executionProfileCode = PROFILE;
        row.directionMode = "LONG_ONLY";
        row.orderSizingMode = "BASE_QUANTITY";
    }

    private static void setContext(WalkForwardStudyRow row) {
        row.executionProfileCode = PROFILE;
        row.directionMode = "LONG_ONLY";
        row.orderSizingMode = "BASE_QUANTITY";
    }

    private static void assertRowContext(String profile, String direction, String sizing) {
        assertEquals(PROFILE, profile);
        assertEquals("LONG_ONLY", direction);
        assertEquals("BASE_QUANTITY", sizing);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(jdbcUrl, username, password);
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
