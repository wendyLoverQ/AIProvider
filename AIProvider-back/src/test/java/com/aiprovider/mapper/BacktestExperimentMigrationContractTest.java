package com.aiprovider.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestExperimentMigrationContractTest {
    @Test
    void v69DefinesExperimentAndCandidatePersistenceContracts() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V69__quant_backtest_experiments.sql"));

        assertTrue(sql.contains("CREATE TABLE q_backtest_experiment ("));
        assertTrue(sql.contains("CREATE TABLE q_backtest_experiment_candidate ("));
        assertTrue(sql.contains("ExperimentId VARCHAR(36) NOT NULL"));
        assertTrue(sql.contains("CandidateId VARCHAR(36) NOT NULL"));
        assertTrue(sql.contains("TrainingRunId VARCHAR(36) NOT NULL"));
        assertTrue(sql.contains("ValidationRunId VARCHAR(36) NOT NULL"));
        assertTrue(sql.contains("CONSTRAINT uq_q_backtest_experiment_id UNIQUE (ExperimentId)"));
        assertTrue(sql.contains("CONSTRAINT uq_q_backtest_experiment_candidate_index UNIQUE (ExperimentId, CandidateIndex)"));
        assertTrue(sql.contains("CONSTRAINT uq_q_backtest_experiment_training_run UNIQUE (TrainingRunId)"));
        assertTrue(sql.contains("CONSTRAINT uq_q_backtest_experiment_validation_run UNIQUE (ValidationRunId)"));
        assertTrue(sql.contains("INDEX ix_q_backtest_experiment_candidate_dispatch (ExperimentId, DispatchStatus, CandidateIndex)"));
        assertTrue(sql.contains("ErrorMessage VARCHAR(1000) NULL"));
    }
}
