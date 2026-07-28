package com.aiprovider.quant.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(
        Instant openTime,
        BigDecimal equityRatio,
        BigDecimal drawdownRatio,
        boolean inPosition,
        BigDecimal equityValue,
        BigDecimal availableCapital,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal positionQuantity,
        BigDecimal positionNotional,
        BigDecimal exposureRatio) {}
