package com.aiprovider.quant.strategy.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.aiprovider.quant.ta4j.Ta4jBarSeriesFactory;
import com.aiprovider.quant.ta4j.Ta4jDataException;
import com.aiprovider.quant.ta4j.Ta4jStrategyFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

public final class Ta4jStrategySignalEngine implements StrategySignalEngine {
    private final StrategyRegistry registry;
    private final Ta4jBarSeriesFactory barSeriesFactory;
    private final Ta4jStrategyFactory strategyFactory;

    public Ta4jStrategySignalEngine() {
        this(new StrategyRegistry());
    }

    public Ta4jStrategySignalEngine(StrategyRegistry registry) {
        this.registry = registry;
        this.barSeriesFactory = new Ta4jBarSeriesFactory();
        this.strategyFactory = new Ta4jStrategyFactory(registry);
    }

    @Override
    public StrategySignalDecision evaluate(StrategySignalRequest request) {
        if (request == null) throw failure("STRATEGY_SIGNAL_REQUEST_INVALID", null, null, null, 0, null, "request is null");
        String code = request.getStrategyCode();
        String symbol = request.getSymbol();
        String interval = request.getInterval() == null ? null : request.getInterval().code();
        int barCount = request.getCandles() == null ? 0 : request.getCandles().size();
        StrategyRuntimePosition position = request.getCurrentPosition();
        try {
            validateRequest(request);
            QuantStrategyDefinition definition;
            try {
                definition = registry.get(code);
            } catch (StrategyException exception) {
                throw failure("STRATEGY_SIGNAL_STRATEGY_NOT_FOUND", code, symbol, interval, barCount, position, exception.getMessage());
            }
            if (!definition.version().equals(request.getStrategyVersion())) {
                throw failure("STRATEGY_SIGNAL_VERSION_NOT_SUPPORTED", code, symbol, interval, barCount, position,
                        "requestedVersion=" + request.getStrategyVersion() + " supportedVersion=" + definition.version());
            }
            if (!definition.supportedMarketTypes().contains(request.getMarketType())) {
                throw failure("STRATEGY_SIGNAL_MARKET_NOT_SUPPORTED", code, symbol, interval, barCount, position,
                        "marketType=" + request.getMarketType());
            }
            StrategyBuildResult build;
            try {
                build = definition.build(request.getStrategyParameters(), barCount);
            } catch (StrategyException exception) {
                String errorCode = "BACKTEST_INSUFFICIENT_BARS".equals(exception.getErrorCode())
                        ? "STRATEGY_SIGNAL_INSUFFICIENT_BARS" : "STRATEGY_SIGNAL_PARAMETER_INVALID";
                throw failure(errorCode, code, symbol, interval, barCount, position, exception.getMessage());
            }
            BarSeries series = barSeriesFactory.create(symbol, request.getInterval(), request.getCandles());
            Strategy strategy = strategyFactory.create(code, series, build);
            int lastIndex = series.getEndIndex();
            boolean matched = position == StrategyRuntimePosition.FLAT
                    ? strategy.shouldEnter(lastIndex) : strategy.shouldExit(lastIndex);
            StrategySignalType signal = position == StrategyRuntimePosition.FLAT
                    ? (matched ? StrategySignalType.ENTER_LONG : StrategySignalType.HOLD)
                    : (matched ? StrategySignalType.EXIT_LONG : StrategySignalType.HOLD);
            StrategySignalDecisionReason reason = position == StrategyRuntimePosition.FLAT
                    ? (matched ? StrategySignalDecisionReason.ENTRY_RULE_MATCHED : StrategySignalDecisionReason.ENTRY_RULE_NOT_MATCHED)
                    : (matched ? StrategySignalDecisionReason.EXIT_RULE_MATCHED : StrategySignalDecisionReason.EXIT_RULE_NOT_MATCHED);
            HistoricalCandle candle = request.getCandles().get(lastIndex);
            return new StrategySignalDecision(code, build.getVersion(), build.getParameters(), request.getProvider(),
                    request.getMarketType(), symbol, request.getInterval(), position, signal, lastIndex, candle, reason);
        } catch (StrategySignalException exception) {
            throw exception;
        } catch (Ta4jDataException exception) {
            String errorCode = "BAR_SERIES_TOO_SHORT".equals(exception.getErrorCode()) || "BAR_SERIES_EMPTY".equals(exception.getErrorCode())
                    ? "STRATEGY_SIGNAL_INSUFFICIENT_BARS" : "STRATEGY_SIGNAL_DATA_INVALID";
            throw failure(errorCode, code, symbol, interval, barCount, position, exception.getErrorCode() + ": " + exception.getMessage());
        } catch (StrategyException exception) {
            throw failure("STRATEGY_SIGNAL_EVALUATION_FAILED", code, symbol, interval, barCount, position, exception.getMessage());
        } catch (RuntimeException exception) {
            throw failure("STRATEGY_SIGNAL_EVALUATION_FAILED", code, symbol, interval, barCount, position, exception.getMessage());
        }
    }

    private void validateRequest(StrategySignalRequest request) {
        if (blank(request.getStrategyCode()) || blank(request.getStrategyVersion()) || request.getProvider() == null
                || request.getMarketType() == null || blank(request.getSymbol()) || request.getInterval() == null
                || request.getCandles() == null || request.getCandles().isEmpty() || request.getCurrentPosition() == null) {
            throw failure("STRATEGY_SIGNAL_REQUEST_INVALID", request.getStrategyCode(), request.getSymbol(),
                    request.getInterval() == null ? null : request.getInterval().code(), request.getCandles() == null ? 0 : request.getCandles().size(),
                    request.getCurrentPosition(), "required field missing");
        }
        for (HistoricalCandle candle : request.getCandles()) {
            if (candle == null || candle.getProvider() != request.getProvider() || candle.getMarketType() != request.getMarketType()
                    || !request.getSymbol().equals(candle.getSymbol()) || candle.getInterval() != request.getInterval()) {
                throw failure("STRATEGY_SIGNAL_DATA_INVALID", request.getStrategyCode(), request.getSymbol(), request.getInterval().code(), request.getCandles().size(),
                        request.getCurrentPosition(), "candle identity does not match request");
            }
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private StrategySignalException failure(String code, String strategyCode, String symbol, String interval, int barCount,
                                            StrategyRuntimePosition position, String detail) {
        return new StrategySignalException(code, "errorCode=" + code + " strategyCode=" + strategyCode + " symbol=" + symbol
                + " interval=" + interval + " barCount=" + barCount
                + " currentPosition=" + position + " detail=" + detail);
    }
}
