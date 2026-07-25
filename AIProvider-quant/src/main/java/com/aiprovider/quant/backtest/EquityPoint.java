package com.aiprovider.quant.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(Instant openTime, BigDecimal equityRatio, BigDecimal drawdownRatio, boolean inPosition) {}
