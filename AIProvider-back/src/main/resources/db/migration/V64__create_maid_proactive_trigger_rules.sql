CREATE TABLE IF NOT EXISTS maid_ProactiveTriggerRules (
    Id BIGINT NOT NULL AUTO_INCREMENT,
    RuleId VARCHAR(128) NOT NULL,
    Enabled TINYINT(1) NOT NULL,
    Event VARCHAR(128) NOT NULL,
    ConditionJson LONGTEXT NOT NULL,
    Priority INT NOT NULL,
    CooldownSeconds INT NOT NULL,
    DisturbanceLevel VARCHAR(64) NOT NULL,
    AllowTts TINYINT(1) NOT NULL,
    ActionTag VARCHAR(128) NOT NULL,
    TextTemplatesJson LONGTEXT NOT NULL,
    Source VARCHAR(128) NOT NULL,
    CreatedAt DATETIME(6) NOT NULL,
    UpdatedAt DATETIME(6) NOT NULL,
    UserId BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (Id),
    UNIQUE KEY UK_maid_ProactiveTriggerRules_RuleId (RuleId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
