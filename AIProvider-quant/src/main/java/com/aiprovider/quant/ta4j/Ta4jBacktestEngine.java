package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.backtest.BacktestMetrics;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.backtest.BacktestResult;
import com.aiprovider.quant.backtest.BacktestTrade;
import com.aiprovider.quant.backtest.EquityPoint;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic Ta4j execution adapter. */
public final class Ta4jBacktestEngine {
    private final Ta4jBarSeriesFactory barsFactory;
    private final Ta4jStrategyFactory strategyFactory;

    public Ta4jBacktestEngine() { this(new Ta4jBarSeriesFactory(), new Ta4jStrategyFactory()); }
    public Ta4jBacktestEngine(Ta4jBarSeriesFactory barsFactory, Ta4jStrategyFactory strategyFactory) {
        this.barsFactory = barsFactory; this.strategyFactory = strategyFactory;
    }

    public BacktestResult run(BacktestRequest request, String symbol, KlineInterval interval,
                              List<HistoricalCandle> candles) {
        validate(request, symbol);
        BarSeries series = barsFactory.create(symbol, interval, candles);
        int last = series.getEndIndex();
        String registeredVersion = new StrategyRegistry().get(request.getStrategyCode()).version();
        if (!registeredVersion.equals(request.getStrategyVersion())) {
            throw new StrategyException("BACKTEST_STRATEGY_VERSION_MISMATCH", "strategy=" + request.getStrategyCode());
        }
        Strategy strategy = strategyFactory.create(request.getStrategyCode(), series, request.getStrategyParameters());
        LinearTransactionCostModel feeModel = new LinearTransactionCostModel(request.getFeeRate());
        BarSeriesManager manager = new BarSeriesManager(series, feeModel, new ZeroCostModel(), new TradeOnNextOpenModel());
        TradingRecord record = manager.run(strategy, Trade.TradeType.BUY, series.numOf(request.getOrderAmount()), 0, last);
        boolean forced = false;
        if (request.isForceCloseAtEnd() && !record.isClosed()) {
            record.exit(last, series.getBar(last).getClosePrice(), series.numOf(request.getOrderAmount()));
            forced = true;
        }
        List<BacktestTrade> trades = toTrades(record, series, candles, request, forced);
        List<EquityPoint> equity = equityCurve(trades, candles, request);
        BacktestMetrics metrics = metrics(trades, equity, candles, request);
        Instant endExclusive = candles.get(candles.size() - 1).getOpenTime().plusMillis(interval.durationMillis());
        return new BacktestResult(request.getStrategyCode(), request.getStrategyVersion(), request.getStrategyParameters(),
                symbol, interval, candles.size(), candles.get(0).getOpenTime(), endExclusive,
                "TA4J_TRADE_ON_NEXT_OPEN", request.getFeeRate(), request.getOrderAmount(), metrics, trades, equity, List.of());
    }

    private List<BacktestTrade> toTrades(TradingRecord record, BarSeries series, List<HistoricalCandle> candles,
                                         BacktestRequest request, boolean forced) {
        List<BacktestTrade> result = new ArrayList<>();
        for (Position position : record.getPositions()) {
            int entry = position.getEntry().getIndex();
            int exit = position.getExit().getIndex();
            BigDecimal entryPrice = bd(position.getEntry().getPricePerAsset());
            BigDecimal exitPrice = bd(position.getExit().getPricePerAsset());
            BigDecimal gross = bd(position.getGrossProfit());
            BigDecimal net = bd(position.getProfit());
            BigDecimal fees = gross.subtract(net);
            BigDecimal amount = bd(position.getEntry().getAmount());
            BigDecimal ratio = net.divide(capital(entryPrice, amount), 12, RoundingMode.HALF_UP);
            result.add(new BacktestTrade(result.size() + 1, entry - 1, entry, candles.get(entry).getOpenTime(), entryPrice,
                    exit - 1, exit, candles.get(exit).getOpenTime(), exitPrice, amount,
                    gross, fees, net, ratio, exit - entry, forced && exit == series.getEndIndex(),
                    forced && exit == series.getEndIndex() ? "FORCED_CLOSE_AT_END" : "SIGNAL_EXIT"));
        }
        return result;
    }

    private List<EquityPoint> equityCurve(List<BacktestTrade> trades, List<HistoricalCandle> candles, BacktestRequest request) {
        List<EquityPoint> points = new ArrayList<>(candles.size());
        BigDecimal equity = BigDecimal.ONE, peak = BigDecimal.ONE;
        for (int i = 0; i < candles.size(); i++) {
            for (BacktestTrade trade : trades) if (trade.getExitIndex() == i) equity = equity.add(trade.getReturnRatio());
            peak = peak.max(equity);
            BigDecimal drawdown = equity.subtract(peak).divide(peak, 12, RoundingMode.HALF_UP);
            final int bar = i;
            boolean inPosition = trades.stream().anyMatch(t -> t.getEntryIndex() <= bar && bar < t.getExitIndex());
            points.add(new EquityPoint(candles.get(i).getOpenTime(), equity, drawdown, inPosition));
        }
        return points;
    }

    private BacktestMetrics metrics(List<BacktestTrade> trades, List<EquityPoint> equity, List<HistoricalCandle> candles, BacktestRequest request) {
        BigDecimal net = BigDecimal.ZERO, grossProfit = BigDecimal.ZERO, grossLoss = BigDecimal.ZERO, fees = BigDecimal.ZERO;
        int wins = 0, losses = 0, breakeven = 0;
        for (BacktestTrade t : trades) { net = net.add(t.getNetProfit()); fees = fees.add(t.getFee()); if (t.getNetProfit().signum() > 0) { wins++; grossProfit = grossProfit.add(t.getGrossProfit()); } else if (t.getNetProfit().signum() < 0) { losses++; grossLoss = grossLoss.add(t.getNetProfit()); } else breakeven++; }
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null : grossProfit.divide(grossLoss.abs(), 12, RoundingMode.HALF_UP);
        BigDecimal totalReturn = equity.get(equity.size() - 1).equityRatio().subtract(BigDecimal.ONE);
        BigDecimal buyHold = bd(candles.get(candles.size() - 1).getClosePrice()).divide(bd(candles.get(0).getClosePrice()), 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal maxDrawdown = equity.stream().map(EquityPoint::drawdownRatio).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal avg = trades.isEmpty() ? null : net.divide(BigDecimal.valueOf(trades.size()), 12, RoundingMode.HALF_UP);
        return new BacktestMetrics(trades.size(), wins, losses, breakeven, trades.isEmpty() ? null : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(trades.size()), 12, RoundingMode.HALF_UP),
                grossProfit, grossLoss, net, totalReturn, maxDrawdown, profitFactor, avg, buyHold, fees);
    }

    private BigDecimal capital(BigDecimal price, BigDecimal amount) { return price.multiply(amount).max(BigDecimal.ONE); }
    private BigDecimal bd(BigDecimal value) { return value; }
    private BigDecimal bd(org.ta4j.core.num.Num n) { return n == null || n.isNaN() ? BigDecimal.ZERO : n.bigDecimalValue(); }
    private void validate(BacktestRequest request, String symbol) {
        if (request == null || request.getStrategyCode() == null || request.getStrategyVersion() == null) throw new BacktestException("BACKTEST_PARAMETER_INVALID", "strategy identity missing");
        if (symbol == null || symbol.isBlank() || !Double.isFinite(request.getOrderAmount()) || request.getOrderAmount() <= 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", "orderAmount invalid");
        if (!Double.isFinite(request.getFeeRate()) || request.getFeeRate() < 0 || request.getFeeRate() > 0.01) throw new BacktestException("BACKTEST_PARAMETER_INVALID", "feeRate invalid");
        if (!request.isForceCloseAtEnd()) throw new BacktestException("BACKTEST_PARAMETER_INVALID", "forceCloseAtEnd must be true");
    }
}
