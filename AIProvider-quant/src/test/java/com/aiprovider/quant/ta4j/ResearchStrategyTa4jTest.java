package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.strategy.StrategyBuildResult;
import com.aiprovider.quant.strategy.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchStrategyTa4jTest {
    @Test
    void rsiEntersAfterWarmupOnOversoldAndExitsAfterRecovery() {
        List<HistoricalCandle> candles = candles(15, 20, 20, 40);
        BarSeries series = new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, candles);
        StrategyBuildResult build = new StrategyRegistry().get("RSI_MEAN_REVERSION_LONG_ONLY").build(Map.of("rsiPeriod", 14, "entryThreshold", 30, "exitThreshold", 55), candles.size());
        Strategy strategy = new Ta4jStrategyFactory().create("RSI_MEAN_REVERSION_LONG_ONLY", series, build);
        assertThat(firstTrue(strategy, series.getBeginIndex(), series.getEndIndex(), true)).isGreaterThanOrEqualTo(14);
        assertThat(firstTrue(strategy, series.getBeginIndex(), series.getEndIndex(), false)).isGreaterThanOrEqualTo(14);
    }

    @Test
    void macdCrossesUpInTrendAndDownInRetreatAfterWarmup() {
        List<HistoricalCandle> candles = candles(20, 25, 25, 30);
        BarSeries series = new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, candles);
        StrategyBuildResult build = new StrategyRegistry().get("MACD_TREND_LONG_ONLY").build(Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), candles.size());
        Strategy strategy = new Ta4jStrategyFactory().create("MACD_TREND_LONG_ONLY", series, build);
        assertThat(firstTrue(strategy, series.getBeginIndex(), series.getEndIndex(), true)).isGreaterThanOrEqualTo(35);
        assertThat(firstTrue(strategy, series.getBeginIndex(), series.getEndIndex(), false)).isGreaterThanOrEqualTo(35);
    }

    @Test
    void enginePreservesResearchStrategyContractAndIsDeterministic() {
        List<HistoricalCandle> candles = candles(20, 25, 25, 30);
        BacktestRequest request = new BacktestRequest("MACD_TREND_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 12, "slowPeriod", 26, "signalPeriod", 9), BigDecimal.ONE, BigDecimal.ZERO, true);
        var first = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, candles);
        var second = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, candles);
        assertThat(first.getStrategyCode()).isEqualTo("MACD_TREND_LONG_ONLY");
        assertThat(first.getStrategyVersion()).isEqualTo("1.0.0");
        assertThat(first.getExecutionModel()).isEqualTo("TA4J_TRADE_ON_NEXT_OPEN");
        assertThat(first.getStrategyParameters()).containsExactlyInAnyOrderEntriesOf(request.getStrategyParameters());
        assertThat(first.getEquityCurve()).hasSize(candles.size());
        assertThat(first.getTrades().stream().map(trade -> List.of(trade.getEntryIndex(), trade.getExitIndex(), trade.getNetProfit(), trade.getReturnRatio())).toList())
                .isEqualTo(second.getTrades().stream().map(trade -> List.of(trade.getEntryIndex(), trade.getExitIndex(), trade.getNetProfit(), trade.getReturnRatio())).toList());
        assertThat(first.getEquityCurve()).isEqualTo(second.getEquityCurve());
    }

    private int firstTrue(Strategy strategy, int begin, int end, boolean entry) {
        for (int i = begin; i <= end; i++) if (entry ? strategy.shouldEnter(i) : strategy.shouldExit(i)) return i;
        return Integer.MAX_VALUE;
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
