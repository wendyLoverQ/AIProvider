package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.backtest.BacktestMetrics;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.backtest.BacktestResult;
import com.aiprovider.quant.backtest.BacktestTrade;
import com.aiprovider.quant.backtest.EquityPoint;
import com.aiprovider.quant.execution.BacktestCompatibilityService;
import com.aiprovider.quant.execution.BacktestMarketContext;
import com.aiprovider.quant.execution.ExecutionProfileDefinition;
import com.aiprovider.quant.execution.ExecutionProfileRegistry;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.portfolio.BacktestCapitalLedger;
import com.aiprovider.quant.portfolio.BacktestPortfolioSnapshot;
import com.aiprovider.quant.strategy.QuantStrategyDefinition;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyException;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Deterministic Ta4j execution adapter. */
public final class Ta4jBacktestEngine {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final Ta4jBarSeriesFactory barsFactory;
    private final StrategyRegistry registry;
    private final Ta4jStrategyFactory strategyFactory;
    private final BacktestCompatibilityService compatibility;
    private final BacktestCapitalLedger capitalLedger;

    public Ta4jBacktestEngine() {
        this(
                new Ta4jBarSeriesFactory(),
                new StrategyRegistry(),
                new ExecutionProfileRegistry());
    }

    public Ta4jBacktestEngine(
            Ta4jBarSeriesFactory barsFactory,
            StrategyRegistry registry,
            ExecutionProfileRegistry executionProfiles) {
        this.barsFactory = barsFactory;
        this.registry = registry;
        this.strategyFactory = new Ta4jStrategyFactory(registry);
        this.compatibility = new BacktestCompatibilityService(executionProfiles);
        this.capitalLedger = new BacktestCapitalLedger();
    }

    public BacktestResult run(BacktestRequest request, BacktestMarketContext market,
                              List<HistoricalCandle> candles) {
        validateRequest(request, market);
        try {
            BarSeries series = barsFactory.create(market.symbol(), market.interval(), candles);
            QuantStrategyDefinition definition = registry.get(request.getStrategyCode());
            ExecutionProfileDefinition profile =
                    compatibility.validate(
                                    request.getExecutionProfileCode(),
                                    request.getDirectionMode(),
                                    request.getOrderSizingMode(),
                                    definition,
                                    market,
                                    request.getStrategyParameters(),
                                    request.getOrderAmount(),
                                    request.getFeeRate())
                            .profile();
            if (!definition.version().equals(request.getStrategyVersion())) {
                throw new StrategyException("BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED", context(request, market, candles, "version"));
            }
            StrategyBuildResult build = definition.build(request.getStrategyParameters(), series.getBarCount());
            Strategy strategy = strategyFactory.create(definition.code(), series, build);
            if (profile.leverage().compareTo(BigDecimal.ONE) != 0) {
                throw new BacktestException(
                        "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                        "unsupported leverage=" + profile.leverage());
            }
            LinearTransactionCostModel feeModel =
                    switch (profile.transactionCostModel()) {
                        case "LINEAR_FEE_RATE" ->
                                new LinearTransactionCostModel(request.getFeeRate().doubleValue());
                        default ->
                                throw new BacktestException(
                                        "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                                        "unsupported transactionCostModel="
                                                + profile.transactionCostModel());
                    };
            ZeroCostModel holdingCostModel =
                    switch (profile.holdingCostModel()) {
                        case "ZERO" -> new ZeroCostModel();
                        default ->
                                throw new BacktestException(
                                        "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                                        "unsupported holdingCostModel=" + profile.holdingCostModel());
                    };
            TradeOnNextOpenModel executionModel =
                    switch (profile.fillModel()) {
                        case "TA4J_TRADE_ON_NEXT_OPEN" -> new TradeOnNextOpenModel();
                        default ->
                                throw new BacktestException(
                                        "BACKTEST_STRATEGY_EXECUTION_INCOMPATIBLE",
                                        "unsupported fillModel=" + profile.fillModel());
                    };
            var amount =
                    switch (profile.orderSizingMode()) {
                        case BASE_QUANTITY -> series.numOf(request.getOrderAmount());
                    };
            BarSeriesManager manager =
                    new BarSeriesManager(series, feeModel, holdingCostModel, executionModel);
            TradingRecord record = manager.run(strategy, tradeType(profile.entryOrderSide()), amount,
                    series.getBeginIndex(), series.getEndIndex());
            boolean forcedClose = request.isForceCloseAtEnd() && !record.isClosed();
            if (forcedClose) {
                record.exit(
                        series.getEndIndex(),
                        series.getBar(series.getEndIndex()).getClosePrice(),
                        amount);
            }
            List<BacktestTrade> trades =
                    toTrades(
                            record,
                            series,
                            candles,
                            forcedClose,
                            profile,
                            request.getFeeRate());
            List<BacktestPortfolioSnapshot> portfolio =
                    capitalLedger.rebuild(
                            request.getInitialCapital(),
                            request.getFeeRate(),
                            trades,
                            candles);
            List<EquityPoint> equity = equityCurve(portfolio);
            BacktestMetrics metrics =
                    metrics(trades, equity, candles, request.getInitialCapital());
            Instant endExclusive = candles.get(candles.size() - 1).getOpenTime().plusMillis(market.interval().durationMillis());
            return new BacktestResult(request.getStrategyCode(), request.getStrategyVersion(), build.getParameters(),
                    market.symbol(), market.interval(), candles.size(), candles.get(0).getOpenTime(), endExclusive,
                    profile.fillModel(), request.getFeeRate(), request.getOrderAmount(),
                    request.getInitialCapital(), metrics.getFinalEquity(), metrics, trades, equity,
                    profile.limitations());
        } catch (BacktestException e) {
            throw e;
        } catch (StrategyException e) {
            throw new BacktestException(e.getErrorCode(), context(request, market, candles, e.getMessage()), e);
        } catch (Ta4jDataException e) {
            throw new BacktestException("BACKTEST_DATA_INVALID", context(request, market, candles, e.getMessage()), e);
        } catch (RuntimeException e) {
            throw new BacktestException("BACKTEST_EXECUTION_FAILED", context(request, market, candles, e.getMessage()), e);
        }
    }

    private List<BacktestTrade> toTrades(TradingRecord record, BarSeries series, List<HistoricalCandle> candles,
                                         boolean forcedClose, ExecutionProfileDefinition profile,
                                         BigDecimal feeRate) {
        List<BacktestTrade> result = new ArrayList<>();
        int last = series.getEndIndex();
        for (Position position : record.getPositions()) {
            int entry = position.getEntry().getIndex();
            int exit = position.getExit().getIndex();
            boolean forced = forcedClose && exit == last;
            BigDecimal entryPrice = forced ? bd(candles.get(entry).getOpenPrice()) : bd(position.getEntry().getPricePerAsset());
            BigDecimal exitPrice = forced ? bd(candles.get(exit).getClosePrice()) : bd(position.getExit().getPricePerAsset());
            BigDecimal amount = bd(position.getEntry().getAmount());
            BigDecimal gross = exitPrice.subtract(entryPrice).multiply(amount);
            BigDecimal fee = entryPrice.add(exitPrice).multiply(amount).multiply(feeRate);
            BigDecimal net = gross.subtract(fee);
            requirePositive(entryPrice, "entryPrice");
            requirePositive(amount, "amount");
            BigDecimal ratio = net.divide(entryPrice.multiply(amount), 12, RoundingMode.HALF_UP);
            Integer exitSignal = forced ? null : exit - 1;
            Instant exitTime = forced ? candles.get(exit).getCloseTime() : candles.get(exit).getOpenTime();
            result.add(new BacktestTrade(result.size() + 1, entry - 1, entry, candles.get(entry).getOpenTime(), entryPrice,
                    exitSignal, exit, exitTime, exitPrice, amount, gross, fee, net, ratio, exit - entry,
                    forced, forced ? "END_OF_SERIES" : "STRATEGY_EXIT", profile.positionSide(),
                    profile.entryOrderSide(), profile.exitOrderSide()));
        }
        return result;
    }

    private List<EquityPoint> equityCurve(List<BacktestPortfolioSnapshot> portfolio) {
        List<EquityPoint> points = new ArrayList<>(portfolio.size());
        BigDecimal peak = portfolio.get(0).initialCapital();
        for (BacktestPortfolioSnapshot snapshot : portfolio) {
            peak = peak.max(snapshot.totalEquity());
            BigDecimal drawdown =
                    peak.subtract(snapshot.totalEquity())
                            .divide(peak, 12, RoundingMode.HALF_UP)
                            .max(BigDecimal.ZERO);
            BigDecimal equityRatio =
                    snapshot
                            .totalEquity()
                            .divide(snapshot.initialCapital(), 12, RoundingMode.HALF_UP);
            points.add(
                    new EquityPoint(
                            snapshot.openTime(),
                            equityRatio,
                            drawdown,
                            snapshot.position().inPosition(),
                            snapshot.totalEquity(),
                            snapshot.availableCapital(),
                            snapshot.realizedPnl(),
                            snapshot.unrealizedPnl(),
                            snapshot.position().quantity(),
                            snapshot.position().positionNotional(),
                            snapshot.exposureRatio()));
        }
        return List.copyOf(points);
    }

    private BacktestMetrics metrics(
            List<BacktestTrade> trades,
            List<EquityPoint> equity,
            List<HistoricalCandle> candles,
            BigDecimal initialCapital) {
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
        BigDecimal finalEquity = equity.get(equity.size() - 1).equityValue();
        BigDecimal totalPnl = finalEquity.subtract(initialCapital);
        BigDecimal totalExposure =
                equity.stream()
                        .map(EquityPoint::exposureRatio)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageExposure =
                totalExposure.divide(
                        BigDecimal.valueOf(equity.size()), 12, RoundingMode.HALF_UP);
        BigDecimal maximumExposure =
                equity.stream()
                        .map(EquityPoint::exposureRatio)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
        return new BacktestMetrics(trades.size(), wins, losses, breakeven, winRate, grossProfit, grossLoss, net,
                totalReturn, maximumDrawdown, profitFactor, average, buyHold, fees, finalEquity,
                totalPnl, averageExposure, maximumExposure);
    }

    private void validateRequest(BacktestRequest request, BacktestMarketContext market) {
        if (request == null) throw new BacktestException("BACKTEST_REQUEST_INVALID", "request=null");
        if (market == null || market.symbol() == null || market.symbol().isBlank() || market.interval() == null || request.getStrategyCode() == null || request.getStrategyVersion() == null) {
            throw new BacktestException("BACKTEST_REQUEST_INVALID", context(request, market, null, "identity"));
        }
        if (request.getOrderAmount() == null || request.getOrderAmount().signum() <= 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, market, null, "orderAmount"));
        if (request.getInitialCapital() == null || request.getInitialCapital().signum() <= 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, market, null, "initialCapital"));
        if (request.getFeeRate() == null || request.getFeeRate().signum() < 0 || request.getFeeRate().compareTo(new BigDecimal("0.01")) > 0) throw new BacktestException("BACKTEST_PARAMETER_INVALID", context(request, market, null, "feeRate"));
    }

    private String context(BacktestRequest request, BacktestMarketContext market, List<HistoricalCandle> candles, String detail) {
        return "strategyCode=" + (request == null ? null : request.getStrategyCode()) + " symbol=" + (market == null ? null : market.symbol()) + " interval=" + (market == null ? null : market.interval())
                + " barCount=" + (candles == null ? 0 : candles.size()) + " detail=" + detail;
    }

    private Trade.TradeType tradeType(OrderSide side) {
        return switch (side) {
            case BUY -> Trade.TradeType.BUY;
            case SELL -> Trade.TradeType.SELL;
        };
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
