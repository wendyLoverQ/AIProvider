package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The only adapter that converts project candles to Ta4j bars. */
public final class Ta4jBarSeriesFactory {
    public BarSeries create(String seriesName, KlineInterval interval, List<HistoricalCandle> candles) {
        validate(seriesName, interval, candles);
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(seriesName)
                .withNumTypeOf(DecimalNum.class)
                .build();
        for (HistoricalCandle candle : candles) {
            ZonedDateTime end = candle.getCloseTime().atZone(ZoneOffset.UTC);
            series.addBar(new BaseBar(Duration.ofMillis(interval.durationMillis()), end,
                    num(candle.getOpenPrice()), num(candle.getHighPrice()), num(candle.getLowPrice()),
                    num(candle.getClosePrice()), num(candle.getVolume()), num(candle.getQuoteVolume()),
                    candle.getTradeCount()));
        }
        return series;
    }

    private void validate(String name, KlineInterval interval, List<HistoricalCandle> candles) {
        if (candles == null || candles.isEmpty()) fail("BAR_SERIES_EMPTY", "candles=0");
        if (candles.size() < 2) fail("BAR_SERIES_TOO_SHORT", "barCount=" + candles.size());
        if (interval == null || !interval.isFixedDuration()) fail("BAR_SERIES_INVALID_CANDLE", "interval invalid");
        Set<Long> seen = new HashSet<>();
        HistoricalCandle previous = null;
        for (HistoricalCandle candle : candles) {
            if (candle == null) fail("BAR_SERIES_INVALID_CANDLE", "null candle");
            if (candle.getProvider() == null || candle.getMarketType() == null || candle.getSymbol() == null
                    || candle.getInterval() == null || candle.getSymbol().isBlank()) {
                fail("BAR_SERIES_INVALID_CANDLE", "dataset identity invalid");
            }
            if (candle.getInterval() != interval || !seen.add(candle.getOpenTime() == null ? Long.MIN_VALUE : candle.getOpenTime().toEpochMilli())) {
                fail(candle.getInterval() != interval ? "BAR_SERIES_MIXED_DATASET" : "BAR_SERIES_DUPLICATE", "openTime invalid");
            }
            if (previous != null) {
                if (!candle.getProvider().equals(previous.getProvider()) || !candle.getMarketType().equals(previous.getMarketType())
                        || !candle.getSymbol().equals(previous.getSymbol())) fail("BAR_SERIES_MIXED_DATASET", "identity changed");
                long delta = candle.getOpenTime().toEpochMilli() - previous.getOpenTime().toEpochMilli();
                if (delta <= 0) fail("BAR_SERIES_UNSORTED", "openTime not increasing");
                if (delta != interval.durationMillis()) fail("BAR_SERIES_GAPPED", "delta=" + delta);
            }
            validateCandle(candle);
            previous = candle;
        }
    }

    private void validateCandle(HistoricalCandle c) {
        BigDecimal open = c.getOpenPrice(), high = c.getHighPrice(), low = c.getLowPrice(), close = c.getClosePrice();
        if (c.getOpenTime() == null || c.getCloseTime() == null
                || !c.getCloseTime().equals(c.getOpenTime().plusMillis(c.getInterval().durationMillis() - 1))
                || open == null || high == null || low == null || close == null || c.getVolume() == null
                || c.getQuoteVolume() == null
                || open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0
                || c.getVolume().signum() < 0 || c.getQuoteVolume().signum() < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0 || high.compareTo(low) < 0
                || c.getTradeCount() < 0) fail("BAR_SERIES_INVALID_CANDLE", "OHLCV invalid");
    }

    private DecimalNum num(BigDecimal value) { return DecimalNum.valueOf(value); }
    private void fail(String code, String detail) { throw new Ta4jDataException(code, detail); }
}
