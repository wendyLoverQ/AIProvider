package com.aiprovider.mapper;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchStudyMigrationContractTest {
  @Test void v72ContainsParentChildUniquenessAndRequiredIndexes() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V72__quant_research_studies.sql"));
    assertTrue(sql.contains("CREATE TABLE q_research_study"));
    assertTrue(sql.contains("ResearchStudyId CHAR(36)"));
    assertTrue(sql.contains("ParameterSpaceJson JSON NOT NULL"));
    assertTrue(sql.contains("ExpandedParameterGridJson JSON NOT NULL"));
    assertTrue(sql.contains("CONSTRAINT uk_research_study_child UNIQUE (WalkForwardStudyId)"));
    assertTrue(sql.contains("ix_research_study_status_updated"));
    assertTrue(sql.contains("ix_research_study_group_created"));
    assertTrue(sql.contains("CHECK (CandidateCount BETWEEN 1 AND 64)"));
    assertTrue(sql.contains("CHECK (ForceCloseAtEnd = TRUE)"));
  }

  @Test void v73AddsAsciiComparisonKeyAndPersistedOosColumns() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V73__quant_research_study_result_query_contract.sql"));
    assertTrue(sql.contains("CHARACTER SET ascii COLLATE ascii_bin NOT NULL"));
    assertTrue(sql.contains("SuccessfulOosFolds INT NULL"));
    assertTrue(sql.contains("OosMaximumDrawdownRatio DECIMAL(38,18) NULL"));
    assertTrue(sql.contains("ParameterChanges INT NULL"));
  }

  @Test void v74AddsRecoverableOosVersionAndIndex() throws Exception {
    String sql = Files.readString(Path.of("src/main/resources/db/migration/V74__quant_walk_forward_oos_recovery.sql"));
    assertTrue(sql.contains("OosAggregateVersion SMALLINT NULL"));
    assertTrue(sql.contains("ix_walk_forward_oos_recovery"));
    assertTrue(sql.contains("Status, OosAggregateVersion, UpdatedAt, Id"));
  }
}
