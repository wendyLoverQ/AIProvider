ALTER TABLE q_walk_forward_study
    ADD COLUMN OosAggregateVersion SMALLINT NULL;

CREATE INDEX ix_walk_forward_oos_recovery
    ON q_walk_forward_study(Status, OosAggregateVersion, UpdatedAt, Id);
