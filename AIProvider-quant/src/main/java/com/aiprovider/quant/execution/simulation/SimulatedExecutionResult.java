package com.aiprovider.quant.execution.simulation;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;

import java.math.BigDecimal;
import java.util.Objects;

public final class SimulatedExecutionResult {
    private final ExecutionOrderSnapshot orderSnapshot;
    private final ExecutionFill fill;
    private final OrderSide side;
    private final BigDecimal originalBidPrice;
    private final BigDecimal originalAskPrice;
    private final BigDecimal slippageBps;
    private final BigDecimal availableTopQuantity;
    private final BigDecimal fillQuantity;
    private final BigDecimal remainingQuantity;
    private final boolean completelyFilled;

    public SimulatedExecutionResult(ExecutionOrderSnapshot orderSnapshot, ExecutionFill fill, OrderSide side,
                                    BigDecimal originalBidPrice, BigDecimal originalAskPrice,
                                    BigDecimal slippageBps, BigDecimal availableTopQuantity,
                                    BigDecimal fillQuantity, BigDecimal remainingQuantity,
                                    boolean completelyFilled) {
        this.orderSnapshot = Objects.requireNonNull(orderSnapshot, "orderSnapshot");
        this.fill = Objects.requireNonNull(fill, "fill");
        this.side = Objects.requireNonNull(side, "side");
        this.originalBidPrice = Objects.requireNonNull(originalBidPrice, "originalBidPrice");
        this.originalAskPrice = Objects.requireNonNull(originalAskPrice, "originalAskPrice");
        this.slippageBps = Objects.requireNonNull(slippageBps, "slippageBps");
        this.availableTopQuantity = Objects.requireNonNull(availableTopQuantity, "availableTopQuantity");
        this.fillQuantity = Objects.requireNonNull(fillQuantity, "fillQuantity");
        this.remainingQuantity = Objects.requireNonNull(remainingQuantity, "remainingQuantity");
        this.completelyFilled = completelyFilled;
    }

    public ExecutionOrderSnapshot getOrderSnapshot() { return orderSnapshot; }
    public ExecutionFill getFill() { return fill; }
    public OrderSide getSide() { return side; }
    public BigDecimal getOriginalBidPrice() { return originalBidPrice; }
    public BigDecimal getOriginalAskPrice() { return originalAskPrice; }
    public BigDecimal getSlippageBps() { return slippageBps; }
    public BigDecimal getAvailableTopQuantity() { return availableTopQuantity; }
    public BigDecimal getFillQuantity() { return fillQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public boolean isCompletelyFilled() { return completelyFilled; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SimulatedExecutionResult that)) return false;
        return completelyFilled == that.completelyFilled && Objects.equals(orderSnapshot, that.orderSnapshot)
                && Objects.equals(fill, that.fill) && side == that.side
                && Objects.equals(originalBidPrice, that.originalBidPrice)
                && Objects.equals(originalAskPrice, that.originalAskPrice)
                && Objects.equals(slippageBps, that.slippageBps)
                && Objects.equals(availableTopQuantity, that.availableTopQuantity)
                && Objects.equals(fillQuantity, that.fillQuantity)
                && Objects.equals(remainingQuantity, that.remainingQuantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderSnapshot, fill, side, originalBidPrice, originalAskPrice, slippageBps,
                availableTopQuantity, fillQuantity, remainingQuantity, completelyFilled);
    }
}
