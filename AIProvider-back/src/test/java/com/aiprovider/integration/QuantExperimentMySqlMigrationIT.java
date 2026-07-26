package com.aiprovider.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class QuantExperimentMySqlMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("aiprovider_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void flywayV1ToV69CreatesTheQuantExperimentSchemaInMySql() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(1, tableCount(jdbc, "q_backtest_experiment"));
        assertEquals(1, tableCount(jdbc, "q_backtest_experiment_candidate"));
        assertEquals("JSON", columnType(jdbc, "q_backtest_experiment", "ParameterGridJson"));
        assertEquals("decimal(38,18)", columnType(jdbc, "q_backtest_experiment", "OrderAmount"));
        assertEquals("decimal(38,18)", columnType(jdbc, "q_backtest_experiment", "FeeRate"));
        assertTrue(indexExists(jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_candidate_index"));
        assertTrue(indexExists(jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_training_run"));
        assertTrue(indexExists(jdbc, "q_backtest_experiment_candidate", "uq_q_backtest_experiment_validation_run"));
        assertTrue(indexExists(jdbc, "q_backtest_experiment_candidate", "ix_q_backtest_experiment_candidate_dispatch"));
    }

    private int tableCount(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table);
    }

    private String columnType(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("SELECT CASE WHEN DATA_TYPE='decimal' THEN LOWER(CONCAT(DATA_TYPE,'(',NUMERIC_PRECISION,',',NUMERIC_SCALE,')')) ELSE UPPER(DATA_TYPE) END FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", String.class, table, column);
    }

    private boolean indexExists(JdbcTemplate jdbc, String table, String index) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?", Integer.class, table, index) > 0;
    }
}
