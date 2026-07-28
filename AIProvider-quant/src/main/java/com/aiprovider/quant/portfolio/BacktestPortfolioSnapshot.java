package com.aiprovider.quant.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable research-account state at the end of one backtest bar. */
public record BacktestPortfolioSnapshot(
        Instant openTime,
        BigDecimal initialCapital,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalEquity,
        BigDecimal availableCapital,
        BacktestPositionSnapshot position,
        BigDecimal exposureRatio) {

    public BacktestPortfolioSnapshot {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(initialCapital, "initialCapital");
        Objects.requireNonNull(realizedPnl, "realizedPnl");
        Objects.requireNonNull(unrealizedPnl, "unrealizedPnl");
        Objects.requireNonNull(totalEquity, "totalEquity");
        Objects.requireNonNull(availableCapital, "availableCapital");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(exposureRatio, "exposureRatio");
    }
}
