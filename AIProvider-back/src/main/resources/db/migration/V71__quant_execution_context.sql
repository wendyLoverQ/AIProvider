ALTER TABLE q_backtest_run
    ADD COLUMN ExecutionProfileCode VARCHAR(64) NULL AFTER StrategyVersion,
    ADD COLUMN DirectionMode VARCHAR(32) NULL AFTER ExecutionProfileCode,
    ADD COLUMN OrderSizingMode VARCHAR(32) NULL AFTER DirectionMode;

ALTER TABLE q_backtest_experiment
    ADD COLUMN ExecutionProfileCode VARCHAR(64) NULL AFTER StrategyVersion,
    ADD COLUMN DirectionMode VARCHAR(32) NULL AFTER ExecutionProfileCode,
    ADD COLUMN OrderSizingMode VARCHAR(32) NULL AFTER DirectionMode;

ALTER TABLE q_walk_forward_study
    ADD COLUMN ExecutionProfileCode VARCHAR(64) NULL AFTER StrategyVersion,
    ADD COLUMN DirectionMode VARCHAR(32) NULL AFTER ExecutionProfileCode,
    ADD COLUMN OrderSizingMode VARCHAR(32) NULL AFTER DirectionMode;

UPDATE q_backtest_run
SET ExecutionProfileCode = 'USDM_PERPETUAL_LONG_ONLY_1X_V1',
    DirectionMode = 'LONG_ONLY',
    OrderSizingMode = 'BASE_QUANTITY'
WHERE MarketType = 'USDM_PERPETUAL';

UPDATE q_backtest_experiment
SET ExecutionProfileCode = 'USDM_PERPETUAL_LONG_ONLY_1X_V1',
    DirectionMode = 'LONG_ONLY',
    OrderSizingMode = 'BASE_QUANTITY'
WHERE MarketType = 'USDM_PERPETUAL';

UPDATE q_walk_forward_study
SET ExecutionProfileCode = 'USDM_PERPETUAL_LONG_ONLY_1X_V1',
    DirectionMode = 'LONG_ONLY',
    OrderSizingMode = 'BASE_QUANTITY'
WHERE MarketType = 'USDM_PERPETUAL';

ALTER TABLE q_backtest_run
    MODIFY COLUMN ExecutionProfileCode VARCHAR(64) NOT NULL,
    MODIFY COLUMN DirectionMode VARCHAR(32) NOT NULL,
    MODIFY COLUMN OrderSizingMode VARCHAR(32) NOT NULL,
    ADD INDEX idx_backtest_run_execution_profile (ExecutionProfileCode, QueuedAt);

ALTER TABLE q_backtest_experiment
    MODIFY COLUMN ExecutionProfileCode VARCHAR(64) NOT NULL,
    MODIFY COLUMN DirectionMode VARCHAR(32) NOT NULL,
    MODIFY COLUMN OrderSizingMode VARCHAR(32) NOT NULL,
    ADD INDEX ix_q_backtest_experiment_execution_profile (ExecutionProfileCode, CreatedAt, Id);

ALTER TABLE q_walk_forward_study
    MODIFY COLUMN ExecutionProfileCode VARCHAR(64) NOT NULL,
    MODIFY COLUMN DirectionMode VARCHAR(32) NOT NULL,
    MODIFY COLUMN OrderSizingMode VARCHAR(32) NOT NULL,
    ADD INDEX ix_walk_forward_study_execution_profile (ExecutionProfileCode, CreatedAt, Id);

ALTER TABLE q_backtest_trade
    ADD COLUMN PositionSide VARCHAR(32) NULL AFTER ExitReason,
    ADD COLUMN EntryOrderSide VARCHAR(16) NULL AFTER PositionSide,
    ADD COLUMN ExitOrderSide VARCHAR(16) NULL AFTER EntryOrderSide;

UPDATE q_backtest_trade
SET PositionSide = 'LONG',
    EntryOrderSide = 'BUY',
    ExitOrderSide = 'SELL';

ALTER TABLE q_backtest_trade
    MODIFY COLUMN PositionSide VARCHAR(32) NOT NULL,
    MODIFY COLUMN EntryOrderSide VARCHAR(16) NOT NULL,
    MODIFY COLUMN ExitOrderSide VARCHAR(16) NOT NULL;
