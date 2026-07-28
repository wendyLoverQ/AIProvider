package com.aiprovider.quant.account.paper;

import java.math.BigDecimal;
import java.util.Objects;

public final class PaperPositionSnapshot {
    private final boolean open;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal averageEntryPrice;
    private final BigDecimal markPrice;
    private final BigDecimal positionNotional;
    private final BigDecimal unrealizedPnl;
    private final String openingClientOrderId;
    private final BigDecimal openTradeNetPnl;

    private PaperPositionSnapshot(
            boolean open,
            String symbol,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            BigDecimal markPrice,
            BigDecimal positionNotional,
            BigDecimal unrealizedPnl,
            String openingClientOrderId,
            BigDecimal openTradeNetPnl) {
        this.open = open;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageEntryPrice = averageEntryPrice;
        this.markPrice = markPrice;
        this.positionNotional = positionNotional;
        this.unrealizedPnl = unrealizedPnl;
        this.openingClientOrderId = openingClientOrderId;
        this.openTradeNetPnl = openTradeNetPnl;
    }

    static PaperPositionSnapshot flat() {
        return new PaperPositionSnapshot(
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO);
    }

    static PaperPositionSnapshot open(
            String symbol,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            BigDecimal markPrice,
            BigDecimal positionNotional,
            BigDecimal unrealizedPnl,
            String openingClientOrderId,
            BigDecimal openTradeNetPnl) {
        return new PaperPositionSnapshot(
                true,
                symbol,
                quantity,
                averageEntryPrice,
                markPrice,
                positionNotional,
                unrealizedPnl,
                openingClientOrderId,
                openTradeNetPnl);
    }

    public boolean isOpen() { return open; }
    public boolean isFlat() { return !open; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAverageEntryPrice() { return averageEntryPrice; }
    public BigDecimal getMarkPrice() { return markPrice; }
    public BigDecimal getPositionNotional() { return positionNotional; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public String getOpeningClientOrderId() { return openingClientOrderId; }
    public BigDecimal getOpenTradeNetPnl() { return openTradeNetPnl; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperPositionSnapshot that)) return false;
        return open == that.open
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(averageEntryPrice, that.averageEntryPrice)
                && Objects.equals(markPrice, that.markPrice)
                && Objects.equals(positionNotional, that.positionNotional)
                && Objects.equals(unrealizedPnl, that.unrealizedPnl)
                && Objects.equals(openingClientOrderId, that.openingClientOrderId)
                && Objects.equals(openTradeNetPnl, that.openTradeNetPnl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                open, symbol, quantity, averageEntryPrice, markPrice, positionNotional,
                unrealizedPnl, openingClientOrderId, openTradeNetPnl);
    }
}
