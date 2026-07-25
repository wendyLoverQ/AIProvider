package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.backtest.BacktestEngine;
import com.aiprovider.quant.backtest.BacktestRequest;
import com.aiprovider.quant.indicator.IndicatorEngine;
import com.aiprovider.quant.indicator.IndicatorRequest;
import com.aiprovider.quant.indicator.IndicatorType;
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
    void backtestReturnsDeterministicSchemaAndForcedClose() {
        List<HistoricalCandle> candles = candles(40);
        var request = new BacktestRequest("EMA_CROSS_LONG_ONLY", "1.0.0",
                Map.of("fastPeriod", 2, "slowPeriod", 4), 1d, 0.001d, true);
        var result = new BacktestEngine().run(request, "BTCUSDT", KlineInterval.M1, candles);
        assertThat(result.getExecutionModel()).isEqualTo("TA4J_TRADE_ON_NEXT_OPEN");
        assertThat(result.getEquityCurve()).hasSize(40);
        assertThat(result.getEquityCurve().get(0).equityRatio()).isEqualByComparingTo("1");
        assertThat(result.getMetrics().getTradeCount()).isGreaterThanOrEqualTo(0);
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
}
