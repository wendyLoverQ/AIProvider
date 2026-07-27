-- Guard V71 before its first persistent DDL. This callback is intentionally
-- checksum-independent from the already released V71 migration.
SET @qec_required_table_count = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'q_backtest_run',
          'q_backtest_experiment',
          'q_walk_forward_study',
          'q_backtest_trade'
      )
);

SET @qec_history_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'flyway_schema_history'
);

SET @qec_v71_succeeded = 0;
SET @qec_history_sql = IF(
    @qec_history_table_exists = 1,
    'SELECT COUNT(*) INTO @qec_v71_succeeded FROM flyway_schema_history WHERE version = ''71'' AND success = 1',
    'SET @qec_v71_succeeded = 0'
);
PREPARE qec_history_statement FROM @qec_history_sql;
EXECUTE qec_history_statement;
DEALLOCATE PREPARE qec_history_statement;

SET @qec_should_preflight =
    @qec_required_table_count = 4 AND @qec_v71_succeeded = 0;

SET @qec_partial_object_count = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND (
          (table_name = 'q_backtest_run'
              AND column_name IN ('ExecutionProfileCode', 'DirectionMode', 'OrderSizingMode'))
          OR
          (table_name = 'q_backtest_experiment'
              AND column_name IN ('ExecutionProfileCode', 'DirectionMode', 'OrderSizingMode'))
          OR
          (table_name = 'q_walk_forward_study'
              AND column_name IN ('ExecutionProfileCode', 'DirectionMode', 'OrderSizingMode'))
          OR
          (table_name = 'q_backtest_trade'
              AND column_name IN ('PositionSide', 'EntryOrderSide', 'ExitOrderSide'))
      )
) + (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND (
          (table_name = 'q_backtest_run'
              AND index_name = 'idx_backtest_run_execution_profile')
          OR
          (table_name = 'q_backtest_experiment'
              AND index_name = 'ix_q_backtest_experiment_execution_profile')
          OR
          (table_name = 'q_walk_forward_study'
              AND index_name = 'ix_walk_forward_study_execution_profile')
      )
);

SET @qec_invalid_market_count = 0;
SET @qec_market_sql = IF(
    @qec_should_preflight,
    'SELECT '
        '(SELECT COUNT(*) FROM q_backtest_run WHERE MarketType IS NULL OR MarketType <> ''USDM_PERPETUAL'') + '
        '(SELECT COUNT(*) FROM q_backtest_experiment WHERE MarketType IS NULL OR MarketType <> ''USDM_PERPETUAL'') + '
        '(SELECT COUNT(*) FROM q_walk_forward_study WHERE MarketType IS NULL OR MarketType <> ''USDM_PERPETUAL'') '
        'INTO @qec_invalid_market_count',
    'SET @qec_invalid_market_count = 0'
);
PREPARE qec_market_statement FROM @qec_market_sql;
EXECUTE qec_market_statement;
DEALLOCATE PREPARE qec_market_statement;

DROP TEMPORARY TABLE IF EXISTS quant_execution_context_v71_preflight_guard;
CREATE TEMPORARY TABLE quant_execution_context_v71_preflight_guard (
    `partial_V71_manual_repair_required` TINYINT NOT NULL,
    `quant_execution_context_V71_preflight_unsupported_market` TINYINT NOT NULL
);

INSERT INTO quant_execution_context_v71_preflight_guard
VALUES (
    IF(@qec_should_preflight AND @qec_partial_object_count > 0, NULL, 1),
    IF(@qec_should_preflight AND @qec_invalid_market_count > 0, NULL, 1)
);

DROP TEMPORARY TABLE quant_execution_context_v71_preflight_guard;
