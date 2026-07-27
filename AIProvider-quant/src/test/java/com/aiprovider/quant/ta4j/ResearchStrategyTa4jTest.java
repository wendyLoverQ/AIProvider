package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.execution.*;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.IsEqualRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ResearchStrategyTa4jTest {
    @Test
    void rsiEntersAfterWarmupOnOversoldAndExitsAfterRecovery() {
        List<HistoricalCandle> candles = candles(15, 20, 20, 40);
        BarSeries series = new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, candles);
        StrategyBuildResult build = new StrategyRegistry().get("RSI_MEAN_REVERSION_LONG_ONLY").build(Map.of("rsiPeriod", 14, "entryThreshold", 30, "exitThreshold", 55), candles.size());
        Strategy strategy = new Ta4jStrategyFactory().create("RSI_MEAN_REVERSION_LONG_ONLY", series, build);
        int rsiPeriod = 14;
        int flatEnd = 14, downEnd = 34, recoveryEnd = 54;
        int entryIndex = requireFirstSatisfied(strategy, series.getBeginIndex(), series.getEndIndex(), true);
        int exitIndex = requireFirstSatisfied(strategy, series.getBeginIndex(), series.getEndIndex(), false);
        assertThat(entryIndex).isBetween(rsiPeriod, downEnd);
        assertThat(exitIndex).isGreaterThan(entryIndex).isGreaterThan(downEnd).isLessThanOrEqualTo(recoveryEnd);
        for (int index = series.getBeginIndex(); index < rsiPeriod; index++) assertThat(strategy.shouldEnter(index)).as("RSI warmup index %s", index).isFalse();
        assertThat(flatEnd).isEqualTo(14);
    }

    @Test
    void macdCrossesUpInTrendAndDownInRetreatAfterWarmup() {
        List<HistoricalCandle> candles = candles(20, 25, 25, 30);
        BarSeries series = new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, candles);
        StrategyBuildResult build = new StrategyRegistry().get("MACD_TREND_LONG_ONLY").build(Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), candles.size());
        Strategy strategy = new Ta4jStrategyFactory().create("MACD_TREND_LONG_ONLY", series, build);
        int slowPeriod = 26, signalPeriod = 9;
        int flatEnd = 19, upEnd = 69, retreatEnd = 99;
        int entryIndex = requireFirstSatisfied(strategy, series.getBeginIndex(), series.getEndIndex(), true);
        int exitIndex = requireFirstSatisfied(strategy, series.getBeginIndex(), series.getEndIndex(), false);
        assertThat(entryIndex).isBetween(slowPeriod + signalPeriod, upEnd);
        assertThat(exitIndex).isGreaterThan(entryIndex).isBetween(upEnd + 1, retreatEnd);
        MACDIndicator macd = new MACDIndicator(new ClosePriceIndicator(series), 12, slowPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);
        assertThat(macd.getValue(entryIndex - 1).isLessThanOrEqual(signal.getValue(entryIndex - 1))).isTrue();
        assertThat(macd.getValue(entryIndex).isGreaterThan(signal.getValue(entryIndex))).isTrue();
        assertThat(macd.getValue(exitIndex - 1).isGreaterThanOrEqual(signal.getValue(exitIndex - 1))).isTrue();
        assertThat(macd.getValue(exitIndex).isLessThan(signal.getValue(exitIndex))).isTrue();
        assertThat(flatEnd).isEqualTo(19);
    }

    @Test
    void enginePreservesAllThreeStrategyContractsAndIsDeterministic() {
        List<HistoricalCandle> candles = candles(20, 25, 25, 30);
        List<BacktestRequest> requests = List.of(
                request("EMA_CROSS_LONG_ONLY", Map.of("fastPeriod", 2, "slowPeriod", 4), true),
                request("RSI_MEAN_REVERSION_LONG_ONLY", Map.of("rsiPeriod", 14, "entryThreshold", 30, "exitThreshold", 55), true),
                request("MACD_TREND_LONG_ONLY", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), true));
        for (BacktestRequest request : requests) {
            var first = new BacktestEngine().run(request, market(), candles);
            var second = new BacktestEngine().run(request, market(), candles);
            assertThat(first.getStrategyCode()).isEqualTo(request.getStrategyCode());
            assertThat(first.getStrategyVersion()).isEqualTo("1.0.0");
            assertThat(first.getExecutionModel()).isEqualTo("TA4J_TRADE_ON_NEXT_OPEN");
            assertThat(first.getStrategyParameters()).containsExactlyInAnyOrderEntriesOf(request.getStrategyParameters());
            assertThat(first.getBarCount()).isEqualTo(candles.size());
            assertThat(first.getEquityCurve()).hasSize(candles.size());
            assertThat(first.getTrades().stream().map(ResearchStrategyTa4jTest::tradeSnapshot).toList())
                    .isEqualTo(second.getTrades().stream().map(ResearchStrategyTa4jTest::tradeSnapshot).toList());
            assertThat(first.getEquityCurve().stream().map(point -> new EquitySnapshot(point.openTime(), point.equityRatio(), point.drawdownRatio(), point.inPosition())).toList())
                    .isEqualTo(second.getEquityCurve().stream().map(point -> new EquitySnapshot(point.openTime(), point.equityRatio(), point.drawdownRatio(), point.inPosition())).toList());
        }
    }

    @Test
    void forceCloseAtEndControlsOnlyTheExplicitEndOfSeriesTrade() {
        List<HistoricalCandle> candles = candles(20, 25, 40, 0);
        BacktestRequest forceClose = request("MACD_TREND_LONG_ONLY", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), true);
        BacktestRequest keepOpen = request("MACD_TREND_LONG_ONLY", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), false);
        var forced = new BacktestEngine().run(forceClose, market(), candles);
        var unforced = new BacktestEngine().run(keepOpen, market(), candles);
        assertThat(forced.getTrades()).isNotEmpty();
        var last = forced.getTrades().get(forced.getTrades().size() - 1);
        assertThat(last.isForcedExit()).isTrue();
        assertThat(last.getExitReason()).isEqualTo("END_OF_SERIES");
        assertThat(last.getExitIndex()).isEqualTo(candles.size() - 1);
        assertThat(last.getExitPrice()).isEqualByComparingTo(candles.get(candles.size() - 1).getClosePrice());
        assertThat(unforced.getTrades()).noneMatch(trade -> trade.isForcedExit() || "END_OF_SERIES".equals(trade.getExitReason()));
    }

    private static TradeSnapshot tradeSnapshot(com.aiprovider.quant.backtest.BacktestTrade trade) {
        return new TradeSnapshot(trade.getTradeNo(), trade.getEntrySignalIndex(), trade.getEntryIndex(), trade.getEntryTime(), trade.getEntryPrice(), trade.getExitSignalIndex(), trade.getExitIndex(), trade.getExitTime(), trade.getExitPrice(), trade.getAmount(), trade.getGrossProfit(), trade.getFee(), trade.getNetProfit(), trade.getReturnRatio(), trade.getBarsHeld(), trade.isForcedExit(), trade.getExitReason());
    }

    private record TradeSnapshot(int tradeNo, Integer entrySignalIndex, int entryIndex, Instant entryTime, BigDecimal entryPrice, Integer exitSignalIndex, int exitIndex, Instant exitTime, BigDecimal exitPrice, BigDecimal amount, BigDecimal grossProfit, BigDecimal fee, BigDecimal netProfit, BigDecimal returnRatio, int barsHeld, boolean forcedExit, String exitReason) {}
    private record EquitySnapshot(Instant openTime, BigDecimal equityRatio, BigDecimal drawdownRatio, boolean inPosition) {}

    private BacktestRequest request(String code, Map<String, Integer> parameters, boolean forceClose) {
        return new BacktestRequest(
                ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1,
                DirectionMode.LONG_ONLY,
                OrderSizingMode.BASE_QUANTITY,
                code,
                "1.0.0",
                parameters,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                forceClose);
    }

    private BacktestMarketContext market() {
        return new BacktestMarketContext(
                MarketProviderId.BINANCE_USDM.name(),
                MarketType.USDM_PERPETUAL,
                "KLINE",
                "BTCUSDT",
                KlineInterval.M1,
                java.util.Set.of(MarketFeature.OHLCV));
    }

    @Test
    void rsiThresholdRulesIncludeEqualityAtBothBoundaries() {
        BarSeries series = new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, candles(3, 0, 0, 0));
        List<BigDecimal> values = List.of(new BigDecimal("29"), new BigDecimal("30"), new BigDecimal("31"));
        Indicator<Num> indicator = new Indicator<>() {
            @Override public Num getValue(int index) { return series.numOf(values.get(index)); }
            @Override public int getUnstableBars() { return 0; }
            @Override public BarSeries getBarSeries() { return series; }
        };
        Num threshold = series.numOf(30);
        Rule entry = new UnderIndicatorRule(indicator, threshold).or(new IsEqualRule(indicator, threshold));
        Rule exit = new OverIndicatorRule(indicator, threshold).or(new IsEqualRule(indicator, threshold));
        assertThat(entry.isSatisfied(0)).isTrue(); assertThat(entry.isSatisfied(1)).isTrue(); assertThat(entry.isSatisfied(2)).isFalse();
        assertThat(exit.isSatisfied(0)).isFalse(); assertThat(exit.isSatisfied(1)).isTrue(); assertThat(exit.isSatisfied(2)).isTrue();
    }

    private int requireFirstSatisfied(Strategy strategy, int begin, int end, boolean entry) {
        for (int index = begin; index <= end; index++) if (entry ? strategy.shouldEnter(index) : strategy.shouldExit(index)) return index;
        fail((entry ? "entry" : "exit") + " signal not found in range " + begin + ".." + end);
        throw new AssertionError("unreachable");
    }

    private List<HistoricalCandle> candles(int flat, int down, int up, int retreat) {
        int[] values = new int[flat + down + up + retreat];
        for (int i = 0; i < values.length; i++) values[i] = i < flat ? 100 : i < flat + down ? 100 - (i - flat + 1) : i < flat + down + up ? 80 + (i - flat - down + 1) * 3 : 155 - (i - flat - down - up + 1) * 3;
        Instant start = Instant.ofEpochMilli(1_700_000_000_000L);
        return java.util.stream.IntStream.range(0, values.length).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(values[i]);
            HistoricalCandle candle = new HistoricalCandle();
            candle.setProvider(MarketProviderId.BINANCE_USDM); candle.setMarketType(MarketType.USDM_PERPETUAL); candle.setSymbol("BTCUSDT"); candle.setInterval(KlineInterval.M1);
            candle.setOpenTime(start.plusMillis(i * 60_000L)); candle.setCloseTime(start.plusMillis(i * 60_000L + 59_999L));
            candle.setOpenPrice(close); candle.setHighPrice(close); candle.setLowPrice(close); candle.setClosePrice(close); candle.setVolume(BigDecimal.ONE); candle.setQuoteVolume(BigDecimal.ONE); candle.setTradeCount(1);
            return candle;
        }).toList();
    }
}
