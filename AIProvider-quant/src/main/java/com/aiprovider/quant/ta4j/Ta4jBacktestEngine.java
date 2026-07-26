package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.backtest.BacktestMetrics;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.backtest.BacktestResult;
import com.aiprovider.quant.backtest.BacktestTrade;
import com.aiprovider.quant.backtest.EquityPoint;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic Ta4j execution adapter. */
public final class Ta4jBacktestEngine {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final Ta4jBarSeriesFactory barsFactory;
    private final StrategyRegistry registry;
    private final Ta4jStrategyFactory strategyFactory;

    public Ta4jBacktestEngine() { this(new Ta4jBarSeriesFactory(), new StrategyRegistry()); }
    public Ta4jBacktestEngine(Ta4jBarSeriesFactory barsFactory, StrategyRegistry registry) {
        this.barsFactory = barsFactory;
        this.registry = registry;
        this.strategyFactory = new Ta4jStrategyFactory(registry);
    }

    public BacktestResult run(BacktestRequest request, String symbol, KlineInterval interval,
                              List<HistoricalCandle> candles) {
        validateRequest(request, symbol, interval);
        try {
            BarSeries series = barsFactory.create(symbol, interval, candles);
            QuantStrategyDefinition definition = registry.get(request.getStrategyCode());
            if (!definition.version().equals(request.getStrategyVersion())) {
                throw new StrategyException("BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED", context(request, symbol, interval, candles, "version"));
            }
            StrategyBuildResult build = definition.build(request.getStrategyParameters(), series.getBarCount());
            Strategy strategy = strategyFactory.create(definition.code(), series, build);
            LinearTransactionCostModel feeModel = new LinearTransactionCostModel(request.getFeeRate().doubleValue());
            BarSeriesManager manager = new BarSeriesManager(series, feeModel, new ZeroCostModel(), new TradeOnNextOpenModel());
            TradingRecord record = manager.run(strategy, Trade.TradeType.BUY, series.numOf(request.getOrderAmount()),
                    series.getBeginIndex(), series.getEndIndex());
            boolean forcedClose = request.isForceCloseAtEnd() && !record.isClosed();
            if (forcedClose) {
                record.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), series.numOf(request.getOrderAmount()));
            }
            List<BacktestTrade> trades = toTrades(record, series, candles, forcedClose);
            List<EquityPoint> equity = equityCurve(record, series, trades, candles);
            BacktestMetrics metrics = metrics(trades, equity, candles);
            Instant endExclusive = candles.get(candles.size() - 1).getOpenTime().plusMillis(interval.durationMillis());
            return new BacktestResult(request.getStrategyCode(), request.getStrategyVersion(), build.getParameters(),
                    symbol, interval, candles.size(), candles.get(0).getOpenTime(), endExclusive,
                    "TA4J_TRADE_ON_NEXT_OPEN", request.getFeeRate(), request.getOrderAmount(), metrics, trades, equity, List.of());
        } catch (BacktestException e) {
            throw e;
        } catch (StrategyException e) {
            throw new BacktestException(e.getErrorCode(), context(request, symbol, interval, candles, e.getMessage()), e);
        } catch (Ta4jDataException e) {
            throw new BacktestException("BACKTEST_DATA_INVALID", context(request, symbol, interval, candles, e.getMessage()), e);
        } catch (RuntimeException e) {
            throw new BacktestException("BACKTEST_EXECUTION_FAILED", context(request, symbol, interval, candles, e.getMessage()), e);
        }
    }

    private List<BacktestTrade> toTrades(TradingRecord record, BarSeries series, List<HistoricalCandle> candles,
                                         boolean forcedClose) {
        List<BacktestTrade> result = new ArrayList<>();
        int last = series.getEndIndex();
        for (Position position : record.getPositions()) {
            int entry = position.getEntry().getIndex();
            int exit = position.getExit().getIndex();
            boolean forced = forcedClose && exit == last;
            BigDecimal entryPrice = forced ? bd(candles.get(entry).getOpenPrice()) : bd(position.getEntry().getPricePerAsset());
            BigDecimal exitPrice = forced ? bd(candles.get(exit).getClosePrice()) : bd(position.getExit().getPricePerAsset());
            BigDecimal amount = bd(position.getEntry().getAmount());
            BigDecimal gross = bd(position.getGrossProfit());
            BigDecimal fee = gross.subtract(bd(position.getProfit()));
            BigDecimal net = gross.subtract(fee);
            requirePositive(entryPrice, "entryPrice");
            requirePositive(amount, "amount");
            BigDecimal ratio = net.divide(entryPrice.multiply(amount), 12, RoundingMode.HALF_UP);
            Integer exitSignal = forced ? null : exit - 1;
            Instant exitTime = forced ? candles.get(exit).getCloseTime() : candles.get(exit).getOpenTime();
            result.add(new BacktestTrade(result.size() + 1, entry - 1, entry, candles.get(entry).getOpenTime(), entryPrice,
                    exitSignal, exit, exitTime, exitPrice, amount, gross, fee, net, ratio, exit - entry,
                    forced, forced ? "END_OF_SERIES" : "STRATEGY_EXIT"));
        }
        return result;
    }

    private List<EquityPoint> equityCurve(TradingRecord record, BarSeries series, List<BacktestTrade> trades, List<HistoricalCandle> candles) {
        List<EquityPoint> points = new ArrayList<>(candles.size());
        CashFlow cashFlow = new CashFlow(series, normalRecord(record));
        Map<Integer, BigDecimal> sameBarMultipliers = sameBarMultipliers(record);
        BigDecimal peak = ONE;
        for (int bar = 0; bar < candles.size(); bar++) {
            BigDecimal equity = requireFinite(cashFlow.getValue(bar), "equity");
            for (Map.Entry<Integer, BigDecimal> sameBar : sameBarMultipliers.entrySet()) {
                if (sameBar.getKey() <= bar) equity = equity.multiply(sameBar.getValue());
            }
            peak = peak.max(equity);
            BigDecimal drawdown = peak.signum() <= 0 ? BigDecimal.ZERO : peak.subtract(equity).divide(peak, 12, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            final int currentBar = bar;
            boolean inPosition = trades.stream().anyMatch(t -> t.getEntryIndex() <= currentBar && currentBar < t.getExitIndex());
            points.add(new EquityPoint(candles.get(bar).getOpenTime(), equity, drawdown, inPosition));
        }
        return points;
    }

    private TradingRecord normalRecord(TradingRecord record) {
        List<Trade> normalTrades = new ArrayList<>();
        for (Position position : record.getPositions()) {
            if (position.getEntry().getIndex() != position.getExit().getIndex()) {
                normalTrades.add(position.getEntry());
                normalTrades.add(position.getExit());
            }
        }
        if (normalTrades.isEmpty()) {
            return new BaseTradingRecord(Trade.TradeType.BUY, record.getTransactionCostModel(), record.getHoldingCostModel());
        }
        return new BaseTradingRecord(record.getTransactionCostModel(), record.getHoldingCostModel(), normalTrades.toArray(Trade[]::new));
    }

    private Map<Integer, BigDecimal> sameBarMultipliers(TradingRecord record) {
        Map<Integer, BigDecimal> multipliers = new HashMap<>();
        for (Position position : record.getPositions()) {
            if (position.getEntry().getIndex() != position.getExit().getIndex()) continue;
            BigDecimal entryNetCost = bd(position.getEntry().getValue()).add(bd(position.getEntry().getCost()));
            BigDecimal exitNetValue = bd(position.getExit().getValue()).subtract(bd(position.getExit().getCost()));
            requirePositive(entryNetCost, "sameBarEntryNetCost");
            BigDecimal multiplier = exitNetValue.divide(entryNetCost, 12, RoundingMode.HALF_UP);
            multipliers.merge(position.getExit().getIndex(), multiplier, BigDecimal::multiply);
        }
        return multipliers;
    }

    private BacktestMetrics metrics(List<BacktestTrade> trades, List<EquityPoint> equity, List<HistoricalCandle> candles) {
        BigDecimal net = BigDecimal.ZERO, grossProfit = BigDecimal.ZERO, grossLoss = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        BigDecimal averageReturn = BigDecimal.ZERO;
        int wins = 0, losses = 0, breakeven = 0;
        for (BacktestTrade trade : trades) {
            net = net.add(trade.getNetProfit()); fees = fees.add(trade.getFee()); averageReturn = averageReturn.add(trade.getReturnRatio());
            if (trade.getNetProfit().signum() > 0) wins++;
            else if (trade.getNetProfit().signum() < 0) losses++;
            else breakeven++;
            if (trade.getGrossProfit().signum() > 0) grossProfit = grossProfit.add(trade.getGrossProfit());
            else if (trade.getGrossProfit().signum() < 0) grossLoss = grossLoss.add(trade.getGrossProfit().abs());
        }
        BigDecimal winRate = trades.isEmpty() ? null : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(trades.size()), 12, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null : grossProfit.divide(grossLoss, 12, RoundingMode.HALF_UP);
        BigDecimal totalReturn = equity.get(equity.size() - 1).equityRatio().subtract(ONE);
        BigDecimal maximumDrawdown = equity.stream().map(EquityPoint::drawdownRatio).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal buyHold = candles.get(candles.size() - 1).getClosePrice().divide(candles.get(0).getClosePrice(), 12, RoundingMode.HALF_UP).subtract(ONE);
        BigDecimal average = trades.isEmpty() ? null : averageReturn.divide(BigDecimal.valueOf(trades.size()), 12, RoundingMode.HALF_UP);
        return new BacktestMetrics(trades.size(), wins, losses, breakeven, winRate, grossProfit, grossLoss, net,
                totalReturn, maximumDrawdown, profitFactor, average, buyHold, fees);
    }

    private void validateRequest(BacktestRequest request, String symbol, KlineInterval interval) {
        if (request == null) throw new BacktestException("BACKTEST_REQUEST_INVALID", "request=null");
        if (symbol == null || symbol.isBlank() || interval == null || request.getStrategyCode() == null || request.getStrategyVersion() == null) {
            throw new BacktestException("BACKTEST_REQUEST_INVALID", context(request, symbol, interval, null, "identity"));
        }
        if (request.getOrderAmount() == null || request.getOrderAmount().signum() <= 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, symbol, interval, null, "orderAmount"));
        if (request.getFeeRate() == null || request.getFeeRate().signum() < 0 || request.getFeeRate().compareTo(new BigDecimal("0.01")) > 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, symbol, interval, null, "feeRate"));
        if (!request.isForceCloseAtEnd()) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, symbol, interval, null, "forceCloseAtEnd=true required"));
    }

    private String context(BacktestRequest request, String symbol, KlineInterval interval, List<HistoricalCandle> candles, String detail) {
        return "strategyCode=" + (request == null ? null : request.getStrategyCode()) + " symbol=" + symbol + " interval=" + interval
                + " barCount=" + (candles == null ? 0 : candles.size()) + " detail=" + detail;
    }

    private BigDecimal bd(BigDecimal value) { return value; }
    private BigDecimal bd(org.ta4j.core.num.Num value) { return requireFinite(value, "numericValue"); }
    private BigDecimal requireFinite(org.ta4j.core.num.Num value, String field) {
        if (value == null || value.isNaN()) throw new BacktestException("BACKTEST_EXECUTION_FAILED", field + " is null/NaN");
        BigDecimal result = value.bigDecimalValue();
        if (result == null) throw new BacktestException("BACKTEST_EXECUTION_FAILED", field + " is null");
        return result;
    }
    private void requirePositive(BigDecimal value, String field) { if (value == null || value.signum() <= 0) throw new BacktestException("BACKTEST_DATA_INVALID", field + " must be positive"); }
}
