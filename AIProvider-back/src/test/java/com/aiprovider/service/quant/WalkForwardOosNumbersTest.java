package com.aiprovider.service.quant;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class WalkForwardOosNumbersTest {
  @Test void normalizesNullAndHalfUpToDatabaseScale() {
    assertNull(WalkForwardOosNumbers.normalize(null));
    assertEquals(18, WalkForwardOosNumbers.normalize(BigDecimal.ZERO).scale());
    assertEquals(new BigDecimal("1.000000000000000001"), WalkForwardOosNumbers.normalize(new BigDecimal("1.0000000000000000005")));
    assertEquals(new BigDecimal("-1.000000000000000001"), WalkForwardOosNumbers.normalize(new BigDecimal("-1.0000000000000000005")));
  }

  @Test void comparesNumericallyAfterNormalization() {
    assertTrue(WalkForwardOosNumbers.numericallyEqual(new BigDecimal("0.1"), new BigDecimal("0.1000000000000000004")));
    assertTrue(WalkForwardOosNumbers.numericallyEqual(null, null));
    assertFalse(WalkForwardOosNumbers.numericallyEqual(null, BigDecimal.ZERO));
    assertFalse(WalkForwardOosNumbers.numericallyEqual(new BigDecimal("0.1"), new BigDecimal("0.100000000000000001")));
    assertEquals(RoundingMode.HALF_UP, WalkForwardOosNumbers.ROUNDING);
  }
}
