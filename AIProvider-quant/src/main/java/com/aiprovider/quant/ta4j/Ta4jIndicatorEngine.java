package com.aiprovider.quant.ta4j;

import com.aiprovider.quant.indicator.IndicatorCalculator;
import com.aiprovider.quant.indicator.IndicatorException;
import com.aiprovider.quant.indicator.IndicatorNamedSeries;
import com.aiprovider.quant.indicator.IndicatorPoint;
import com.aiprovider.quant.indicator.IndicatorRequest;
import com.aiprovider.quant.indicator.IndicatorResult;
import com.aiprovider.quant.indicator.IndicatorType;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.model.KlineInterval;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Ta4j implementation of the provider-neutral indicator port. */
public final class Ta4jIndicatorEngine implements IndicatorCalculator {
    private final Ta4jBarSeriesFactory factory;

    public Ta4jIndicatorEngine() { this(new Ta4jBarSeriesFactory()); }
    public Ta4jIndicatorEngine(Ta4jBarSeriesFactory factory) { this.factory = factory; }

    @Override
    public IndicatorResult calculate(String seriesName, KlineInterval interval, List<HistoricalCandle> candles,
                                     IndicatorRequest request) {
        if (request == null || request.getIndicatorType() == null) {
            throw new IndicatorException("INDICATOR_TYPE_NOT_SUPPORTED", "indicatorType missing");
        }
        BarSeries bars = factory.create(seriesName, interval, candles);
        Map<String, Integer> parameters = resolve(request);
        Indicator<Num> close = new ClosePriceIndicator(bars);
        List<IndicatorNamedSeries> output = new ArrayList<>();
        int unstable;
        switch (request.getIndicatorType()) {
            case SMA -> {
                Indicator<Num> value = new SMAIndicator(close, parameters.get("period"));
                unstable = effectiveUnstable(value, candles.size());
                output.add(named("value", value, candles, unstable));
            }
            case EMA -> {
                Indicator<Num> value = new EMAIndicator(close, parameters.get("period"));
                unstable = effectiveUnstable(value, candles.size());
                output.add(named("value", value, candles, unstable));
            }
            case RSI -> {
                Indicator<Num> value = new RSIIndicator(close, parameters.get("period"));
                unstable = effectiveUnstable(value, candles.size());
                output.add(named("value", value, candles, unstable));
            }
            case ATR -> {
                Indicator<Num> value = new ATRIndicator(bars, parameters.get("period"));
                unstable = effectiveUnstable(value, candles.size());
                output.add(named("value", value, candles, unstable));
            }
            case MACD -> {
                MACDIndicator macd = new MACDIndicator(close, parameters.get("fastPeriod"), parameters.get("slowPeriod"));
                Indicator<Num> signal = macd.getSignalLine(parameters.get("signalPeriod"));
                unstable = Math.max(effectiveUnstable(macd, candles.size()), effectiveUnstable(signal, candles.size()));
                output.add(named("macd", macd, candles, unstable));
                output.add(named("signal", signal, candles, unstable));
                output.add(named("histogram", macd.getHistogram(parameters.get("signalPeriod")), candles, unstable));
            }
            default -> throw new IndicatorException("INDICATOR_TYPE_NOT_SUPPORTED", "indicator=" + request.getIndicatorType());
        }
        if (candles.size() <= unstable) {
            throw new IndicatorException("INDICATOR_INSUFFICIENT_BARS", "barCount=" + candles.size() + " unstableBars=" + unstable);
        }
        return new IndicatorResult(request.getIndicatorType(), parameters, unstable, output);
    }

    private Map<String, Integer> resolve(IndicatorRequest request) {
        Map<String, Integer> p = request.getParameters();
        switch (request.getIndicatorType()) {
            case SMA, EMA, RSI, ATR -> {
                rejectUnknown(p, "period");
                return Map.of("period", requiredPeriod(p, "period"));
            }
            case MACD -> {
                rejectUnknown(p, "fastPeriod", "slowPeriod", "signalPeriod");
                int fast = requiredPeriod(p, "fastPeriod");
                int slow = requiredPeriod(p, "slowPeriod");
                int signal = requiredPeriod(p, "signalPeriod");
                if (fast >= slow) throw new IndicatorException("INDICATOR_PARAMETER_INVALID", "fastPeriod must be less than slowPeriod");
                return Map.of("fastPeriod", fast, "slowPeriod", slow, "signalPeriod", signal);
            }
            default -> throw new IndicatorException("INDICATOR_TYPE_NOT_SUPPORTED", "indicator=" + request.getIndicatorType());
        }
    }

    private int requiredPeriod(Map<String, Integer> parameters, String name) {
        Integer value = parameters.get(name);
        if (value == null) throw new IndicatorException("INDICATOR_PARAMETER_MISSING", name);
        if (value < 2 || value > 1000) throw new IndicatorException("INDICATOR_PARAMETER_INVALID", name + "=" + value);
        return value;
    }

    private void rejectUnknown(Map<String, Integer> parameters, String... allowed) {
        for (String key : parameters.keySet()) {
            boolean known = false;
            for (String name : allowed) known |= name.equals(key);
            if (!known) throw new IndicatorException("INDICATOR_PARAMETER_INVALID", "unknown=" + key);
        }
    }

    private IndicatorNamedSeries named(String name, Indicator<Num> indicator, List<HistoricalCandle> candles, int unstable) {
        List<IndicatorPoint> points = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            Num value = indicator.getValue(i);
            boolean stable = i >= unstable && value != null && !value.isNaN();
            points.add(new IndicatorPoint(candles.get(i).getOpenTime(), stable ? value.bigDecimalValue() : null, stable));
        }
        return new IndicatorNamedSeries(name, points);
    }

    private int effectiveUnstable(Indicator<Num> indicator, int barCount) {
        int firstStable = barCount;
        for (int i = 0; i < barCount; i++) {
            Num value = indicator.getValue(i);
            if (value != null && !value.isNaN()) { firstStable = i; break; }
        }
        return Math.max(indicator.getUnstableBars(), firstStable);
    }
}
