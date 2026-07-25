package com.aiprovider.quant.indicator;

import java.util.List;

public final class IndicatorNamedSeries {
    private final String name;
    private final List<IndicatorPoint> points;
    public IndicatorNamedSeries(String name, List<IndicatorPoint> points) { this.name = name; this.points = List.copyOf(points); }
    public String getName() { return name; }
    public List<IndicatorPoint> getPoints() { return points; }
}
