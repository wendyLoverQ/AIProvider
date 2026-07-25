package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.indicator.IndicatorEngine;
import com.aiprovider.quant.indicator.IndicatorRequest;
import com.aiprovider.quant.indicator.IndicatorType;
import com.aiprovider.quant.indicator.IndicatorException;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ta4jQuantCoreTest {
    @Test
    void indicatorHasStableBigDecimalPointsAndRejectsGaps() {
        List<HistoricalCandle> candles = candles(12);
        var result = new IndicatorEngine().calculate("BTCUSDT", KlineInterval.M1, candles,
                new IndicatorRequest(IndicatorType.SMA, Map.of("period", 3)));
        assertThat(result.getUnstableBars()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSeries().get(0).getPoints()).hasSize(12);
        assertThat(result.getSeries().get(0).getPoints().get(0).getValue()).isNull();
        assertThat(result.getSeries().get(0).getPoints().get(4).isStable()).isTrue();

        List<HistoricalCandle> gap = candles(12);
        gap.get(5).setOpenTime(gap.get(5).getOpenTime().plusMillis(KlineInterval.M1.durationMillis()));
        gap.get(5).setCloseTime(gap.get(5).getOpenTime().plusMillis(KlineInterval.M1.durationMillis() - 1));
        assertThatThrownBy(() -> new Ta4jBarSeriesFactory().create("BTCUSDT", KlineInterval.M1, gap))
                .isInstanceOf(Ta4jDataException.class)
                .extracting(e -> ((Ta4jDataException) e).getErrorCode()).isEqualTo("BAR_SERIES_GAPPED");
    }

    @Test
    void indicatorHistoryIsStableWhenFutureBarsAreAppended() {
        List<HistoricalCandle> history = candles(40);
        List<HistoricalCandle> extended = new java.util.ArrayList<>(history);
        extended.addAll(candles(5).stream().map(c -> {
            c.setOpenTime(c.getOpenTime().plusMillis(40 * 60_000L));
            c.setCloseTime(c.getCloseTime().plusMillis(40 * 60_000L));
            return c;
        }).toList());
        for (IndicatorRequest request : List.of(
                new IndicatorRequest(IndicatorType.EMA, Map.of("period", 4)),
                new IndicatorRequest(IndicatorType.RSI, Map.of("period", 4)),
                new IndicatorRequest(IndicatorType.MACD, Map.of("fastPeriod", 2, "slowPeriod", 4, "signalPeriod", 2)))) {
            var before = new IndicatorEngine().calculate("BTCUSDT", KlineInterval.M1, history, request);
            var after = new IndicatorEngine().calculate("BTCUSDT", KlineInterval.M1, extended, request);
            for (int series = 0; series < before.getSeries().size(); series++) {
                for (int i = 0; i < history.size(); i++) {
                    if (before.getSeries().get(series).getPoints().get(i).isStable()) {
                        assertThat(after.getSeries().get(series).getPoints().get(i).getValue())
                                .isEqualByComparingTo(before.getSeries().get(series).getPoints().get(i).getValue());
                    }
                }
            }
        }
    }

    @Test
    void indicatorRejectsUnknownParametersAndInsufficientBars() {
        assertThatThrownBy(() -> new IndicatorEngine().calculate("BTCUSDT", KlineInterval.M1, candles(10),
                new IndicatorRequest(IndicatorType.SMA, Map.of("period", 3, "extra", 1))))
                .isInstanceOf(IndicatorException.class)
                .extracting(e -> ((IndicatorException) e).getErrorCode()).isEqualTo("INDICATOR_PARAMETER_INVALID");
        assertThatThrownBy(() -> new IndicatorEngine().calculate("BTCUSDT", KlineInterval.M1, candles(2),
                new IndicatorRequest(IndicatorType.EMA, Map.of("period", 4))))
                .isInstanceOf(IndicatorException.class)
                .extracting(e -> ((IndicatorException) e).getErrorCode()).isEqualTo("INDICATOR_INSUFFICIENT_BARS");
    }

    @Test
    void backtestReturnsDeterministicSchemaAndForcedClose() {
        List<HistoricalCandle> candles = trendCandles(40, false);
        var request = new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0",
                Map.of("fastPeriod", 2, "slowPeriod", 4), BigDecimal.ONE, new BigDecimal("0.001"), true);
        var result = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, candles);
        assertThat(result.getExecutionModel()).isEqualTo("TA4J_TRADE_ON_NEXT_OPEN");
        assertThat(result.getStrategyParameters()).containsExactlyInAnyOrderEntriesOf(Map.of("fastPeriod", 2, "slowPeriod", 4));
        assertThat(result.getTrades()).isNotEmpty();
        var forced = result.getTrades().get(result.getTrades().size() - 1);
        assertThat(forced.getEntrySignalIndex()).isNotNegative();
        assertThat(forced.getEntryPrice()).isEqualByComparingTo(candles.get(forced.getEntryIndex()).getOpenPrice());
        assertThat(forced.getEntryPrice()).isNotEqualByComparingTo(candles.get(forced.getEntrySignalIndex()).getClosePrice());
        assertThat(forced.isForcedExit()).isTrue();
        assertThat(forced.getExitSignalIndex()).isNull();
        assertThat(forced.getExitReason()).isEqualTo("END_OF_SERIES");
        assertThat(forced.getExitTime()).isEqualTo(candles.get(candles.size() - 1).getCloseTime());
        assertThat(result.getEquityCurve()).hasSize(40);
        assertThat(result.getEquityCurve().get(0).equityRatio()).isEqualByComparingTo("1");
        assertThat(result.getMetrics().getTradeCount()).isEqualTo(result.getMetrics().getWinningTradeCount()
                + result.getMetrics().getLosingTradeCount() + result.getMetrics().getBreakEvenTradeCount());
        assertThat(result.getMetrics().getMaximumDrawdownRatio()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.getMetrics().getTotalReturnRatio()).isEqualByComparingTo(
                result.getEquityCurve().get(result.getEquityCurve().size() - 1).equityRatio().subtract(BigDecimal.ONE));
    }

    @Test
    void emptyStrategyParametersResolveDefaultsWithoutTa4jNpe() {
        var definition = new com.aiprovider.quant.strategy.EmaCrossLongOnlyDefinition();
        var result = definition.build(Map.of(), 40);
        assertThat(result.getParameters()).containsExactlyInAnyOrderEntriesOf(Map.of("fastPeriod", 12, "slowPeriod", 26));
        assertThat(definition.minimumRequiredBars(Map.of())).isEqualTo(result.getMinimumRequiredBars());
        assertThat(definition.minimumRequiredBars(Map.of("fastPeriod", 2, "slowPeriod", 4))).isEqualTo(5);
        assertThat(definition.build(Map.of("fastPeriod", 2, "slowPeriod", 4), 5).getMinimumRequiredBars()).isEqualTo(5);
        assertThatThrownBy(() -> definition.build(Map.of("fastPeriod", 2, "slowPeriod", 4), 4))
                .isInstanceOf(com.aiprovider.quant.strategy.StrategyException.class)
                .extracting(e -> ((com.aiprovider.quant.strategy.StrategyException) e).getErrorCode())
                .isEqualTo("BACKTEST_INSUFFICIENT_BARS");
    }

    @Test
    void noTradeCurveRemainsFlat() {
        var request = new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0", Map.of(), BigDecimal.ONE, BigDecimal.ZERO, true);
        var result = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, candles(40));
        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getEquityCurve()).allSatisfy(point -> {
            assertThat(point.equityRatio()).isEqualByComparingTo("1");
            assertThat(point.drawdownRatio()).isEqualByComparingTo("0");
        });
    }

    @Test
    void sameBarForcedCloseKeepsTradeAndFiniteCashFlow() {
        List<HistoricalCandle> input = sameBarSignalCandles(true);
        var request = new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4), BigDecimal.ONE, new BigDecimal("0.001"), true);
        var result = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, input);
        assertThat(result.getTrades()).hasSize(1);
        var trade = result.getTrades().get(0);
        assertThat(trade.getEntrySignalIndex()).isEqualTo(input.size() - 2);
        assertThat(trade.getEntryIndex()).isEqualTo(input.size() - 1);
        assertThat(trade.getExitIndex()).isEqualTo(input.size() - 1);
        assertThat(trade.getBarsHeld()).isZero();
        assertThat(trade.getExitSignalIndex()).isNull();
        assertThat(trade.getEntryPrice()).isEqualByComparingTo(input.get(input.size() - 1).getOpenPrice());
        assertThat(trade.getExitPrice()).isEqualByComparingTo(input.get(input.size() - 1).getClosePrice());
        assertThat(trade.getExitTime()).isEqualTo(input.get(input.size() - 1).getCloseTime());
        assertThat(trade.isForcedExit()).isTrue();
        assertThat(trade.getExitReason()).isEqualTo("END_OF_SERIES");
        assertThat(result.getEquityCurve()).allSatisfy(point -> {
            assertThat(point.equityRatio()).isNotNull();
            assertThat(point.drawdownRatio()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
        assertThat(result.getEquityCurve().get(result.getEquityCurve().size() - 1).equityRatio().subtract(BigDecimal.ONE))
                .isEqualByComparingTo(result.getMetrics().getTotalReturnRatio());
    }

    @Test
    void sameBarLossWithoutFeeStillProducesFiniteDownwardEquity() {
        List<HistoricalCandle> input = sameBarSignalCandles(false);
        var result = new BacktestEngine().run(new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4), BigDecimal.ONE, BigDecimal.ZERO, true), "BTCUSDT", KlineInterval.M1, input);
        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getNetProfit()).isNegative();
        assertThat(result.getMetrics().getTotalFees()).isZero();
        assertThat(result.getMetrics().getTotalReturnRatio()).isNegative();
        assertThat(result.getEquityCurve()).allSatisfy(point -> assertThat(point.equityRatio()).isNotNull());
    }

    @Test
    void feesReduceNetAndReturnButDoNotChangeGross() {
        List<HistoricalCandle> candles = trendCandles(40, false);
        var noFee = new BacktestEngine().run(new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4), BigDecimal.ONE, BigDecimal.ZERO, true), "BTCUSDT", KlineInterval.M1, candles);
        var withFee = new BacktestEngine().run(new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0", Map.of("fastPeriod", 2, "slowPeriod", 4), BigDecimal.ONE, new BigDecimal("0.001"), true), "BTCUSDT", KlineInterval.M1, candles);
        assertThat(withFee.getTrades()).hasSameSizeAs(noFee.getTrades());
        assertThat(withFee.getMetrics().getTotalFees()).isGreaterThan(BigDecimal.ZERO);
        assertThat(withFee.getMetrics().getNetProfit()).isLessThan(noFee.getMetrics().getNetProfit());
        assertThat(withFee.getMetrics().getTotalReturnRatio()).isLessThanOrEqualTo(noFee.getMetrics().getTotalReturnRatio());
        for (int i = 0; i < noFee.getTrades().size(); i++) {
            assertThat(withFee.getTrades().get(i).getGrossProfit()).isEqualByComparingTo(noFee.getTrades().get(i).getGrossProfit());
        }
    }

    private List<HistoricalCandle> candles(int count) {
        Instant start = Instant.ofEpochMilli(1_700_000_000_000L);
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(100 + (i < 15 ? i : 30 - i));
            HistoricalCandle c = new HistoricalCandle();
            c.setProvider(MarketProviderId.BINANCE_USDM); c.setMarketType(MarketType.USDM_PERPETUAL);
            c.setSymbol("BTCUSDT"); c.setInterval(KlineInterval.M1);
            c.setOpenTime(start.plusMillis(i * 60_000L)); c.setCloseTime(start.plusMillis(i * 60_000L + 59_999L));
            c.setOpenPrice(close); c.setHighPrice(close); c.setLowPrice(close); c.setClosePrice(close);
            c.setVolume(BigDecimal.ONE); c.setQuoteVolume(BigDecimal.ONE); c.setTradeCount(1);
            return c;
        }).toList();
    }

    private List<HistoricalCandle> trendCandles(int count, boolean reversal) {
        Instant start = Instant.ofEpochMilli(1_700_000_000_000L);
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> {
            int closeValue = i < 8 ? 110 - i : (reversal && i > 25 ? 118 - (i - 25) : 102 + i);
            BigDecimal close = BigDecimal.valueOf(closeValue);
            BigDecimal open = close.add(BigDecimal.valueOf(5));
            HistoricalCandle c = new HistoricalCandle();
            c.setProvider(MarketProviderId.BINANCE_USDM); c.setMarketType(MarketType.USDM_PERPETUAL); c.setSymbol("BTCUSDT"); c.setInterval(KlineInterval.M1);
            c.setOpenTime(start.plusMillis(i * 60_000L)); c.setCloseTime(start.plusMillis(i * 60_000L + 59_999L));
            c.setOpenPrice(open); c.setHighPrice(open.add(BigDecimal.ONE)); c.setLowPrice(close.subtract(BigDecimal.ONE)); c.setClosePrice(close);
            c.setVolume(BigDecimal.ONE); c.setQuoteVolume(BigDecimal.ONE); c.setTradeCount(1);
            return c;
        }).toList();
    }

    private List<HistoricalCandle> sameBarSignalCandles(boolean profitable) {
        Instant start = Instant.ofEpochMilli(1_700_000_000_000L);
        int[] closes = {100, 99, 98, 97, 120, profitable ? 130 : 90};
        return java.util.stream.IntStream.range(0, closes.length).mapToObj(i -> {
            BigDecimal close = BigDecimal.valueOf(closes[i]);
            BigDecimal open = i == closes.length - 1 ? (profitable ? close.subtract(BigDecimal.valueOf(5)) : close.add(BigDecimal.valueOf(5))) : close;
            HistoricalCandle c = new HistoricalCandle();
            c.setProvider(MarketProviderId.BINANCE_USDM); c.setMarketType(MarketType.USDM_PERPETUAL); c.setSymbol("BTCUSDT"); c.setInterval(KlineInterval.M1);
            c.setOpenTime(start.plusMillis(i * 60_000L)); c.setCloseTime(start.plusMillis(i * 60_000L + 59_999L));
            c.setOpenPrice(open); c.setHighPrice(open.max(close).add(BigDecimal.ONE)); c.setLowPrice(open.min(close).subtract(BigDecimal.ONE)); c.setClosePrice(close);
            c.setVolume(BigDecimal.ONE); c.setQuoteVolume(BigDecimal.ONE); c.setTradeCount(1);
            return c;
        }).toList();
    }
}
