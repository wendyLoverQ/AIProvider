package com.aiprovider.quant.backtest;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.execution.BacktestMarketContext;
import com.aiprovider.quant.ta4j.Ta4jBacktestEngine;
import com.aiprovider.quant.strategy.StrategyRegistry;

import java.util.List;

/** Public backtest API; Ta4j types do not cross this boundary. */
public final class BacktestEngine {
    private final Ta4jBacktestEngine delegate;

    public BacktestEngine() { this(new StrategyRegistry()); }
    public BacktestEngine(StrategyRegistry registry) { this.delegate = new Ta4jBacktestEngine(new com.aiprovider.quant.ta4j.Ta4jBarSeriesFactory(), registry); }
    public BacktestResult run(BacktestRequest request, BacktestMarketContext market,
                              List<HistoricalCandle> candles) {
        try {
            return delegate.run(request, market, candles);
        } catch (BacktestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BacktestException("BACKTEST_EXECUTION_FAILED",
                    "strategyCode=" + (request == null ? null : request.getStrategyCode()) + " symbol="
                            + (market == null ? null : market.symbol()) + " interval="
                            + (market == null ? null : market.interval()) + " barCount="
                            + (candles == null ? 0 : candles.size()), e);
        }
    }
}
