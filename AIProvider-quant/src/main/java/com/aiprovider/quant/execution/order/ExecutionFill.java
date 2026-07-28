package com.aiprovider.quant.execution.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class ExecutionFill {
    private final String fillId;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal fee;
    private final String feeAsset;
    private final Instant filledAt;

    public ExecutionFill(String fillId, BigDecimal quantity, BigDecimal price, BigDecimal fee, String feeAsset, Instant filledAt) {
        if (blank(fillId) || quantity == null || quantity.signum() <= 0 || price == null || price.signum() <= 0
                || fee == null || fee.signum() < 0 || blank(feeAsset) || filledAt == null) {
            throw new ExecutionOrderException("EXECUTION_ORDER_FILL_INVALID", "fill field is missing or invalid");
        }
        this.fillId = fillId;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.feeAsset = feeAsset;
        this.filledAt = filledAt;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public String getFillId() { return fillId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFee() { return fee; }
    public String getFeeAsset() { return feeAsset; }
    public Instant getFilledAt() { return filledAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExecutionFill that)) return false;
        return Objects.equals(fillId, that.fillId) && Objects.equals(quantity, that.quantity)
                && Objects.equals(price, that.price) && Objects.equals(fee, that.fee)
                && Objects.equals(feeAsset, that.feeAsset) && Objects.equals(filledAt, that.filledAt);
    }
    @Override public int hashCode() { return Objects.hash(fillId, quantity, price, fee, feeAsset, filledAt); }
}
