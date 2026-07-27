ALTER TABLE q_research_study
    MODIFY ComparisonGroupKey CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE q_walk_forward_study
    ADD COLUMN SuccessfulOosFolds INT NULL,
    ADD COLUMN FailedFolds INT NULL,
    ADD COLUMN HasOosGaps BOOLEAN NULL,
    ADD COLUMN OosTotalReturnRatio DECIMAL(38,18) NULL,
    ADD COLUMN OosMaximumDrawdownRatio DECIMAL(38,18) NULL,
    ADD COLUMN OosTradeCount INT NULL,
    ADD COLUMN OosTotalFees DECIMAL(38,18) NULL,
    ADD COLUMN ParameterChanges INT NULL;
