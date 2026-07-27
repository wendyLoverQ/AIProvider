package com.aiprovider.service.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WalkForwardOosNumbers {
  public static final int SCALE = 18;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private WalkForwardOosNumbers() {}

  public static BigDecimal normalize(BigDecimal value) {
    return value == null ? null : value.setScale(SCALE, ROUNDING);
  }

  public static boolean numericallyEqual(BigDecimal left, BigDecimal right) {
    BigDecimal normalizedLeft = normalize(left);
    BigDecimal normalizedRight = normalize(right);
    if (normalizedLeft == null || normalizedRight == null) return normalizedLeft == normalizedRight;
    return normalizedLeft.compareTo(normalizedRight) == 0;
  }
}
