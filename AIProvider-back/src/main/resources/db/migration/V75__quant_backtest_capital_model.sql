ALTER TABLE q_backtest_run
    ADD COLUMN InitialCapital DECIMAL(38,18) NULL AFTER ResolvedParametersJson,
    ADD COLUMN FinalEquity DECIMAL(38,18) NULL AFTER TotalFees,
    ADD COLUMN TotalPnl DECIMAL(38,18) NULL AFTER FinalEquity,
    ADD COLUMN AverageExposureRatio DECIMAL(38,18) NULL AFTER TotalPnl,
    ADD COLUMN MaximumExposureRatio DECIMAL(38,18) NULL AFTER AverageExposureRatio;

ALTER TABLE q_backtest_equity
    ADD COLUMN EquityValue DECIMAL(38,18) NULL AFTER InPosition,
    ADD COLUMN AvailableCapital DECIMAL(38,18) NULL AFTER EquityValue,
    ADD COLUMN RealizedPnl DECIMAL(38,18) NULL AFTER AvailableCapital,
    ADD COLUMN UnrealizedPnl DECIMAL(38,18) NULL AFTER RealizedPnl,
    ADD COLUMN PositionQuantity DECIMAL(38,18) NULL AFTER UnrealizedPnl,
    ADD COLUMN PositionNotional DECIMAL(38,18) NULL AFTER PositionQuantity,
    ADD COLUMN ExposureRatio DECIMAL(38,18) NULL AFTER PositionNotional;

ALTER TABLE q_backtest_experiment
    ADD COLUMN InitialCapital DECIMAL(38,18) NULL AFTER ValidationEndOpenTimeMs;

ALTER TABLE q_walk_forward_study
    ADD COLUMN InitialCapital DECIMAL(38,18) NULL AFTER MinimumTrainTrades;

ALTER TABLE q_research_study
    ADD COLUMN InitialCapital DECIMAL(38,18) NULL AFTER MinimumTrainTrades;
