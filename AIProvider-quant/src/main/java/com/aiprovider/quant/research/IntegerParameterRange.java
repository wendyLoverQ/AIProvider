package com.aiprovider.quant.research;

import java.util.ArrayList;
import java.util.List;

public record IntegerParameterRange(String parameterName, int minimum, int maximum, int step) {
    public IntegerParameterRange {
        if (parameterName == null || parameterName.isBlank()) throw new IllegalArgumentException("parameterName must not be blank");
        if (minimum > maximum) throw new IllegalArgumentException("minimum must not exceed maximum");
        if (step <= 0) throw new IllegalArgumentException("step must be positive");
        long distance = (long) maximum - minimum;
        if (distance % step != 0) throw new IllegalArgumentException("range is not divisible by step");
        long count = distance / step + 1;
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("range contains too many values");
    }

    public int valueCount() {
        return (int) (((long) maximum - minimum) / step + 1);
    }

    public List<Integer> values() {
        List<Integer> result = new ArrayList<>(valueCount());
        for (long value = minimum; value <= maximum; value += step) result.add((int) value);
        return List.copyOf(result);
    }
}
