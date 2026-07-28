package com.aiprovider.quant.ta4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.execution.BacktestMarketContext;
import com.aiprovider.quant.execution.DirectionMode;
import com.aiprovider.quant.execution.ExecutionProfileCode;
import com.aiprovider.quant.execution.MarketFeature;
import com.aiprovider.quant.execution.OrderSizingMode;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ta4jBacktestCapitalModelTest {
    private static final BigDecimal INITIAL_CAPITAL = new BigDecimal("1000");
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");

    @Test
    void noTradeKeepsCapitalFlatAndExposureAtZero() {
        var result =
                new BacktestEngine()
                        .run(
                                request(INITIAL_CAPITAL, BigDecimal.ONE, FEE_RATE),
                                market(),
                                flatCandles(40));

        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getInitialCapital()).isEqualByComparingTo(INITIAL_CAPITAL);
        assertThat(result.getFinalEquity()).isEqualByComparingTo(INITIAL_CAPITAL);
        assertThat(result.getMetrics().getFinalEquity())
                .isEqualByComparingTo(INITIAL_CAPITAL);
        assertThat(result.getMetrics().getTotalPnl()).isZero();
        assertThat(result.getMetrics().getAverageExposureRatio()).isZero();
        assertThat(result.getMetrics().getMaximumExposureRatio()).isZero();
        assertThat(result.getEquityCurve())
                .allSatisfy(
                        point -> {
                            assertThat(point.equityValue())
                                    .isEqualByComparingTo(INITIAL_CAPITAL);
                            assertThat(point.positionQuantity()).isZero();
                            assertThat(point.positionNotional()).isZero();
                            assertThat(point.unrealizedPnl()).isZero();
                            assertThat(point.exposureRatio()).isZero();
                        });
    }

    @Test
    void profitableTradeChargesEntryImmediatelyAndSettlesToTradeNetProfit() {
        var result =
                new BacktestEngine()
                        .run(
                                request(INITIAL_CAPITAL, BigDecimal.ONE, FEE_RATE),
                                market(),
                                profitableCandles());

        assertThat(result.getTrades()).hasSize(1);
        var trade = result.getTrades().get(0);
        var entryPoint = result.getEquityCurve().get(trade.getEntryIndex());
        BigDecimal entryFee =
                trade.getEntryPrice().multiply(trade.getAmount()).multiply(FEE_RATE);
        BigDecimal expectedEntryUnrealized =
                trade.getAmount()
                        .multiply(
                                profitableCandles()
                                        .get(trade.getEntryIndex())
                                        .getClosePrice()
                                        .subtract(trade.getEntryPrice()));

        assertThat(entryPoint.inPosition()).isTrue();
        assertThat(entryPoint.realizedPnl()).isEqualByComparingTo(entryFee.negate());
        assertThat(entryPoint.unrealizedPnl())
                .isEqualByComparingTo(expectedEntryUnrealized);
        assertThat(entryPoint.equityValue())
                .isEqualByComparingTo(
                        INITIAL_CAPITAL.subtract(entryFee).add(expectedEntryUnrealized));
        assertThat(entryPoint.positionQuantity()).isEqualByComparingTo(trade.getAmount());
        assertThat(entryPoint.positionNotional()).isPositive();
        assertThat(entryPoint.exposureRatio()).isPositive();

        var finalPoint = result.getEquityCurve().get(result.getEquityCurve().size() - 1);
        assertThat(finalPoint.inPosition()).isFalse();
        assertThat(finalPoint.exposureRatio()).isZero();
        assertThat(finalPoint.positionQuantity()).isZero();
        assertThat(finalPoint.positionNotional()).isZero();
        assertThat(result.getFinalEquity())
                .isEqualByComparingTo(INITIAL_CAPITAL.add(trade.getNetProfit()));
        assertThat(result.getMetrics().getTotalPnl())
                .isEqualByComparingTo(trade.getNetProfit());
        assertThat(result.getMetrics().getNetProfit())
                .isEqualByComparingTo(trade.getNetProfit());
    }

    @Test
    void insufficientCapitalFailsWithoutReducingTheBaseQuantity() {
        BigDecimal requestedQuantity = new BigDecimal("2");

        assertThatThrownBy(
                        () ->
                                new BacktestEngine()
                                        .run(
                                                request(
                                                        new BigDecimal("100"),
                                                        requestedQuantity,
                                                        FEE_RATE),
                                                market(),
                                                profitableCandles()))
                .isInstanceOf(BacktestException.class)
                .satisfies(
                        error -> {
                            BacktestException failure = (BacktestException) error;
                            assertThat(failure.getErrorCode())
                                    .isEqualTo("BACKTEST_CAPITAL_INSUFFICIENT");
                            assertThat(failure.getMessage())
                                    .contains(
                                            "currentEquity=",
                                            "entryPrice=",
                                            "orderQuantity="
                                                    + requestedQuantity.toPlainString(),
                                            "entryNotional=",
                                            "entryFee=");
                        });
    }

    @Test
    void sameBarEntryAndForcedExitChargeEachFeeExactlyOnce() {
        var result =
                new BacktestEngine()
                        .run(
                                request(INITIAL_CAPITAL, BigDecimal.ONE, FEE_RATE),
                                market(),
                                sameBarTradeCandles());

        assertThat(result.getTrades()).hasSize(1);
        var trade = result.getTrades().get(0);
        assertThat(trade.getEntryIndex()).isEqualTo(trade.getExitIndex());
        BigDecimal expectedFee =
                trade.getEntryPrice()
                        .add(trade.getExitPrice())
                        .multiply(trade.getAmount())
                        .multiply(FEE_RATE);
        BigDecimal expectedNet =
                trade.getExitPrice()
                        .subtract(trade.getEntryPrice())
                        .multiply(trade.getAmount())
                        .subtract(expectedFee);

        assertThat(trade.getFee()).isEqualByComparingTo(expectedFee);
        assertThat(trade.getNetProfit()).isEqualByComparingTo(expectedNet);
        assertThat(result.getMetrics().getTotalFees()).isEqualByComparingTo(expectedFee);
        assertThat(result.getFinalEquity())
                .isEqualByComparingTo(INITIAL_CAPITAL.add(expectedNet));
        assertThat(result.getEquityCurve().get(trade.getExitIndex()).inPosition()).isFalse();
    }

    private BacktestRequest request(
            BigDecimal initialCapital, BigDecimal amount, BigDecimal feeRate) {
        return new BacktestRequest(
                ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1,
                DirectionMode.LONG_ONLY,
                OrderSizingMode.BASE_QUANTITY,
                "EMA_CROSS_LONG_ONLY",
                "1.0.0",
                Map.of("fastPeriod", 2, "slowPeriod", 4),
                initialCapital,
                amount,
                feeRate,
                true);
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

    private List<HistoricalCandle> flatCandles(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> candle(index, BigDecimal.valueOf(100), BigDecimal.valueOf(100)))
                .toList();
    }

    private List<HistoricalCandle> profitableCandles() {
        return java.util.stream.IntStream.range(0, 40)
                .mapToObj(
                        index -> {
                            BigDecimal close =
                                    BigDecimal.valueOf(index < 8 ? 110 - index : 102 + index);
                            return candle(index, close.add(BigDecimal.valueOf(5)), close);
                        })
                .toList();
    }

    private List<HistoricalCandle> sameBarTradeCandles() {
        int[] closes = {100, 99, 98, 97, 120, 130};
        return java.util.stream.IntStream.range(0, closes.length)
                .mapToObj(
                        index -> {
                            BigDecimal close = BigDecimal.valueOf(closes[index]);
                            BigDecimal open =
                                    index == closes.length - 1
                                            ? close.subtract(BigDecimal.valueOf(5))
                                            : close;
                            return candle(index, open, close);
                        })
                .toList();
    }

    private HistoricalCandle candle(int index, BigDecimal open, BigDecimal close) {
        Instant openTime =
                Instant.ofEpochMilli(1_700_000_000_000L).plusMillis(index * 60_000L);
        HistoricalCandle candle = new HistoricalCandle();
        candle.setProvider(MarketProviderId.BINANCE_USDM);
        candle.setMarketType(MarketType.USDM_PERPETUAL);
        candle.setSymbol("BTCUSDT");
        candle.setInterval(KlineInterval.M1);
        candle.setOpenTime(openTime);
        candle.setCloseTime(openTime.plusMillis(59_999L));
        candle.setOpenPrice(open);
        candle.setHighPrice(open.max(close).add(BigDecimal.ONE));
        candle.setLowPrice(open.min(close).subtract(BigDecimal.ONE));
        candle.setClosePrice(close);
        candle.setVolume(BigDecimal.ONE);
        candle.setQuoteVolume(BigDecimal.ONE);
        candle.setTradeCount(1);
        return candle;
    }
}
