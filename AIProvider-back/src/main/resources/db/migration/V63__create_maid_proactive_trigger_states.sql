CREATE TABLE IF NOT EXISTS maid_ProactiveTriggerStates (
    Id BIGINT NOT NULL AUTO_INCREMENT,
    RuleId VARCHAR(128) NOT NULL,
    LastTriggeredAt DATETIME(6) NULL,
    TriggerCount INT NOT NULL,
    LastResult LONGTEXT NOT NULL,
    UpdatedAt DATETIME(6) NOT NULL,
    UserId BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (Id),
    UNIQUE KEY UK_maid_ProactiveTriggerStates_RuleId (RuleId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
