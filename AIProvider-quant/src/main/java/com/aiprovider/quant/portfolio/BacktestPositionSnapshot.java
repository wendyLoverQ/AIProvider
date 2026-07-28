package com.aiprovider.quant.portfolio;

import com.aiprovider.quant.execution.PositionSide;
import java.math.BigDecimal;
import java.util.Objects;

/** Immutable long-only position state at the end of a backtest bar. */
public record BacktestPositionSnapshot(
        boolean inPosition,
        PositionSide positionSide,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal markPrice,
        BigDecimal positionNotional,
        BigDecimal unrealizedPnl) {

    public BacktestPositionSnapshot {
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(positionNotional, "positionNotional");
        Objects.requireNonNull(unrealizedPnl, "unrealizedPnl");
        if (inPosition) {
            if (positionSide != PositionSide.LONG) {
                throw new IllegalArgumentException("Only LONG positions are supported");
            }
            Objects.requireNonNull(entryPrice, "entryPrice");
            Objects.requireNonNull(markPrice, "markPrice");
            if (quantity.signum() <= 0 || entryPrice.signum() <= 0 || markPrice.signum() <= 0) {
                throw new IllegalArgumentException("Open position quantity and prices must be positive");
            }
        } else if (positionSide != null
                || entryPrice != null
                || markPrice != null
                || quantity.signum() != 0
                || positionNotional.signum() != 0
                || unrealizedPnl.signum() != 0) {
            throw new IllegalArgumentException("Flat position must contain explicit zero amounts");
        }
    }

    public static BacktestPositionSnapshot flat() {
        return new BacktestPositionSnapshot(
                false,
                null,
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
