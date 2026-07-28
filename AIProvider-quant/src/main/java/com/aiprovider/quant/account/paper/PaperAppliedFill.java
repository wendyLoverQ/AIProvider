package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.execution.order.ExecutionFill;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PaperAppliedFill {
    private final String clientOrderId;
    private final String fillId;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final BigDecimal fee;
    private final String feeAsset;
    private final Instant filledAt;

    private PaperAppliedFill(
            String clientOrderId,
            String fillId,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fee,
            String feeAsset,
            Instant filledAt) {
        this.clientOrderId = clientOrderId;
        this.fillId = fillId;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.feeAsset = feeAsset;
        this.filledAt = filledAt;
    }

    static PaperAppliedFill from(String clientOrderId, ExecutionFill fill) {
        return new PaperAppliedFill(
                clientOrderId,
                fill.getFillId(),
                fill.getQuantity(),
                fill.getPrice(),
                fill.getFee(),
                fill.getFeeAsset(),
                fill.getFilledAt());
    }

    boolean hasKey(String candidateClientOrderId, String candidateFillId) {
        return clientOrderId.equals(candidateClientOrderId) && fillId.equals(candidateFillId);
    }

    public String getClientOrderId() { return clientOrderId; }
    public String getFillId() { return fillId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getFee() { return fee; }
    public String getFeeAsset() { return feeAsset; }
    public Instant getFilledAt() { return filledAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperAppliedFill that)) return false;
        return Objects.equals(clientOrderId, that.clientOrderId)
                && Objects.equals(fillId, that.fillId)
                && Objects.equals(quantity, that.quantity)
                && Objects.equals(price, that.price)
                && Objects.equals(fee, that.fee)
                && Objects.equals(feeAsset, that.feeAsset)
                && Objects.equals(filledAt, that.filledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientOrderId, fillId, quantity, price, fee, feeAsset, filledAt);
    }
}
