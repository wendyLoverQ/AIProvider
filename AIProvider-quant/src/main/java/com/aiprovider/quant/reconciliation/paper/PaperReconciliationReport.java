package com.aiprovider.quant.reconciliation.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PaperReconciliationReport {
    private final PaperReconciliationStatus status;
    private final List<PaperReconciliationViolation> violations;
    private final int orderCount;
    private final int orderFillCount;
    private final int accountAppliedFillCount;
    private final BigDecimal derivedPositionQuantity;
    private final String derivedOpeningClientOrderId;
    private final BigDecimal derivedAverageEntryPrice;
    private final Instant latestFillTime;
    private final Instant reconciledAt;

    PaperReconciliationReport(
            List<PaperReconciliationViolation> violations,
            int orderCount,
            int orderFillCount,
            int accountAppliedFillCount,
            BigDecimal derivedPositionQuantity,
            String derivedOpeningClientOrderId,
            BigDecimal derivedAverageEntryPrice,
            Instant latestFillTime,
            Instant reconciledAt) {
        this.violations = List.copyOf(violations);
        this.status = violations.isEmpty()
                ? PaperReconciliationStatus.CONSISTENT
                : PaperReconciliationStatus.INCONSISTENT;
        this.orderCount = orderCount;
        this.orderFillCount = orderFillCount;
        this.accountAppliedFillCount = accountAppliedFillCount;
        this.derivedPositionQuantity = derivedPositionQuantity;
        this.derivedOpeningClientOrderId = derivedOpeningClientOrderId;
        this.derivedAverageEntryPrice = derivedAverageEntryPrice;
        this.latestFillTime = latestFillTime;
        this.reconciledAt = reconciledAt;
    }

    public PaperReconciliationStatus getStatus() { return status; }
    public List<PaperReconciliationViolation> getViolations() { return violations; }
    public int getOrderCount() { return orderCount; }
    public int getOrderFillCount() { return orderFillCount; }
    public int getAccountAppliedFillCount() { return accountAppliedFillCount; }
    public BigDecimal getDerivedPositionQuantity() { return derivedPositionQuantity; }
    public String getDerivedOpeningClientOrderId() { return derivedOpeningClientOrderId; }
    public BigDecimal getDerivedAverageEntryPrice() { return derivedAverageEntryPrice; }
    public Instant getLatestFillTime() { return latestFillTime; }
    public Instant getReconciledAt() { return reconciledAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperReconciliationReport that)) return false;
        return orderCount == that.orderCount
                && orderFillCount == that.orderFillCount
                && accountAppliedFillCount == that.accountAppliedFillCount
                && status == that.status
                && Objects.equals(violations, that.violations)
                && numericEquals(derivedPositionQuantity, that.derivedPositionQuantity)
                && Objects.equals(derivedOpeningClientOrderId, that.derivedOpeningClientOrderId)
                && numericEquals(derivedAverageEntryPrice, that.derivedAverageEntryPrice)
                && Objects.equals(latestFillTime, that.latestFillTime)
                && Objects.equals(reconciledAt, that.reconciledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                status, violations, orderCount, orderFillCount, accountAppliedFillCount,
                normalized(derivedPositionQuantity), derivedOpeningClientOrderId,
                normalized(derivedAverageEntryPrice), latestFillTime, reconciledAt);
    }

    private static boolean numericEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static BigDecimal normalized(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
