package com.aiprovider.quant.indicator;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.ta4j.Ta4jBarSeriesFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndicatorEngine {
    private final Ta4jBarSeriesFactory factory;
    public IndicatorEngine() { this(new Ta4jBarSeriesFactory()); }
    public IndicatorEngine(Ta4jBarSeriesFactory factory) { this.factory = factory; }

    public IndicatorResult calculate(String seriesName, KlineInterval interval, List<HistoricalCandle> candles, IndicatorRequest request) {
        if (request == null || request.getIndicatorType() == null) throw new IndicatorException("INDICATOR_TYPE_NOT_SUPPORTED", "indicatorType missing");
        BarSeries bars = factory.create(seriesName, interval, candles);
        Map<String, Integer> p = request.getParameters();
        Indicator<Num> value;
        Indicator<Num> close = new ClosePriceIndicator(bars);
        List<IndicatorNamedSeries> output = new ArrayList<>();
        int unstable;
        switch (request.getIndicatorType()) {
            case SMA -> { int period = period(p, "period"); value = new SMAIndicator(close, period); unstable = value.getUnstableBars(); output.add(named("value", value, candles, unstable)); }
            case EMA -> { int period = period(p, "period"); value = new EMAIndicator(close, period); unstable = value.getUnstableBars(); output.add(named("value", value, candles, unstable)); }
            case RSI -> { int period = period(p, "period"); value = new RSIIndicator(close, period); unstable = value.getUnstableBars(); output.add(named("value", value, candles, unstable)); }
            case ATR -> { int period = period(p, "period"); value = new ATRIndicator(bars, period); unstable = value.getUnstableBars(); output.add(named("value", value, candles, unstable)); }
            case MACD -> {
                int fast = period(p, "fastPeriod"), slow = period(p, "slowPeriod"), signal = period(p, "signalPeriod");
                if (fast >= slow) throw new IndicatorException("INDICATOR_PARAMETER_INVALID", "fastPeriod must be less than slowPeriod");
                MACDIndicator macd = new MACDIndicator(close, fast, slow);
                Indicator<Num> signalLine = macd.getSignalLine(signal);
                output.add(named("macd", macd, candles, Math.max(macd.getUnstableBars(), signalLine.getUnstableBars())));
                output.add(named("signal", signalLine, candles, Math.max(macd.getUnstableBars(), signalLine.getUnstableBars())));
                output.add(named("histogram", macd.getHistogram(signal), candles, Math.max(macd.getUnstableBars(), signalLine.getUnstableBars())));
                unstable = Math.max(macd.getUnstableBars(), signalLine.getUnstableBars());
            }
            default -> throw new IndicatorException("INDICATOR_TYPE_NOT_SUPPORTED", "indicator=" + request.getIndicatorType());
        }
        return new IndicatorResult(request.getIndicatorType(), p, unstable, output);
    }

    private int period(Map<String, Integer> p, String name) {
        Integer value = p.get(name);
        if (value == null) throw new IndicatorException("INDICATOR_PARAMETER_MISSING", name);
        if (value < 2 || value > 1000) throw new IndicatorException("INDICATOR_PARAMETER_INVALID", name + "=" + value);
        return value;
    }

    private IndicatorNamedSeries named(String name, Indicator<Num> indicator, List<HistoricalCandle> candles, int unstable) {
        List<IndicatorPoint> points = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            Num n = indicator.getValue(i);
            boolean stable = i >= unstable && n != null && !n.isNaN();
            points.add(new IndicatorPoint(candles.get(i).getOpenTime(), stable ? n.bigDecimalValue() : null, stable));
        }
        return new IndicatorNamedSeries(name, points);
    }
}
