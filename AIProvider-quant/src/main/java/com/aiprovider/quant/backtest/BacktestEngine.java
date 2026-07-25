package com.aiprovider.quant.backtest;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.ta4j.Ta4jBacktestEngine;

import java.util.List;

/** Public backtest API; Ta4j types do not cross this boundary. */
public final class BacktestEngine {
    private final Ta4jBacktestEngine delegate;

    public BacktestEngine() { this.delegate = new Ta4jBacktestEngine(); }
    public BacktestResult run(BacktestRequest request, String symbol, KlineInterval interval,
                              List<HistoricalCandle> candles) {
        try {
            return delegate.run(request, symbol, interval, candles);
        } catch (BacktestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BacktestException("BACKTEST_EXECUTION_FAILED",
                    "strategyCode=" + (request == null ? null : request.getStrategyCode()) + " symbol=" + symbol
                            + " interval=" + interval + " barCount=" + (candles == null ? 0 : candles.size()), e);
        }
    }
}
