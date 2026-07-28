package com.aiprovider.quant.risk.pretrade;

import com.aiprovider.quant.execution.OrderSide;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class PreTradeRiskDecision {
    private final PreTradeRiskDecisionStatus decisionStatus;
    private final String clientOrderId;
    private final OrderSide orderSide;
    private final BigDecimal orderQuantity;
    private final BigDecimal referencePrice;
    private final BigDecimal orderNotional;
    private final BigDecimal estimatedFee;
    private final BigDecimal currentPositionQuantity;
    private final BigDecimal projectedPositionQuantity;
    private final BigDecimal currentPositionNotional;
    private final BigDecimal projectedPositionNotional;
    private final BigDecimal totalEquity;
    private final BigDecimal projectedEquity;
    private final BigDecimal availableCapital;
    private final BigDecimal projectedAvailableCapital;
    private final BigDecimal orderNotionalRatio;
    private final BigDecimal projectedExposureRatio;
    private final BigDecimal projectedRemainingCapitalRatio;
    private final BigDecimal dailyLossRatio;
    private final List<PreTradeRiskViolation> violations;

    public PreTradeRiskDecision(
            PreTradeRiskDecisionStatus decisionStatus,
            String clientOrderId,
            OrderSide orderSide,
            BigDecimal orderQuantity,
            BigDecimal referencePrice,
            BigDecimal orderNotional,
            BigDecimal estimatedFee,
            BigDecimal currentPositionQuantity,
            BigDecimal projectedPositionQuantity,
            BigDecimal currentPositionNotional,
            BigDecimal projectedPositionNotional,
            BigDecimal totalEquity,
            BigDecimal projectedEquity,
            BigDecimal availableCapital,
            BigDecimal projectedAvailableCapital,
            BigDecimal orderNotionalRatio,
            BigDecimal projectedExposureRatio,
            BigDecimal projectedRemainingCapitalRatio,
            BigDecimal dailyLossRatio,
            List<PreTradeRiskViolation> violations) {
        this.decisionStatus = Objects.requireNonNull(decisionStatus, "decisionStatus");
        this.clientOrderId = Objects.requireNonNull(clientOrderId, "clientOrderId");
        this.orderSide = Objects.requireNonNull(orderSide, "orderSide");
        this.orderQuantity = Objects.requireNonNull(orderQuantity, "orderQuantity");
        this.referencePrice = Objects.requireNonNull(referencePrice, "referencePrice");
        this.orderNotional = Objects.requireNonNull(orderNotional, "orderNotional");
        this.estimatedFee = Objects.requireNonNull(estimatedFee, "estimatedFee");
        this.currentPositionQuantity = Objects.requireNonNull(currentPositionQuantity, "currentPositionQuantity");
        this.projectedPositionQuantity = Objects.requireNonNull(projectedPositionQuantity, "projectedPositionQuantity");
        this.currentPositionNotional = Objects.requireNonNull(currentPositionNotional, "currentPositionNotional");
        this.projectedPositionNotional = Objects.requireNonNull(projectedPositionNotional, "projectedPositionNotional");
        this.totalEquity = Objects.requireNonNull(totalEquity, "totalEquity");
        this.projectedEquity = Objects.requireNonNull(projectedEquity, "projectedEquity");
        this.availableCapital = Objects.requireNonNull(availableCapital, "availableCapital");
        this.projectedAvailableCapital = Objects.requireNonNull(projectedAvailableCapital, "projectedAvailableCapital");
        this.orderNotionalRatio = Objects.requireNonNull(orderNotionalRatio, "orderNotionalRatio");
        this.projectedExposureRatio = Objects.requireNonNull(projectedExposureRatio, "projectedExposureRatio");
        this.projectedRemainingCapitalRatio = Objects.requireNonNull(projectedRemainingCapitalRatio, "projectedRemainingCapitalRatio");
        this.dailyLossRatio = Objects.requireNonNull(dailyLossRatio, "dailyLossRatio");
        this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (decisionStatus == PreTradeRiskDecisionStatus.APPROVED && !this.violations.isEmpty()) {
            throw new IllegalArgumentException("approved decision must not have violations");
        }
        if (decisionStatus == PreTradeRiskDecisionStatus.REJECTED && this.violations.isEmpty()) {
            throw new IllegalArgumentException("rejected decision must have violations");
        }
    }

    public PreTradeRiskDecisionStatus getDecisionStatus() { return decisionStatus; }
    public String getClientOrderId() { return clientOrderId; }
    public OrderSide getOrderSide() { return orderSide; }
    public BigDecimal getOrderQuantity() { return orderQuantity; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getOrderNotional() { return orderNotional; }
    public BigDecimal getEstimatedFee() { return estimatedFee; }
    public BigDecimal getCurrentPositionQuantity() { return currentPositionQuantity; }
    public BigDecimal getProjectedPositionQuantity() { return projectedPositionQuantity; }
    public BigDecimal getCurrentPositionNotional() { return currentPositionNotional; }
    public BigDecimal getProjectedPositionNotional() { return projectedPositionNotional; }
    public BigDecimal getTotalEquity() { return totalEquity; }
    public BigDecimal getProjectedEquity() { return projectedEquity; }
    public BigDecimal getAvailableCapital() { return availableCapital; }
    public BigDecimal getProjectedAvailableCapital() { return projectedAvailableCapital; }
    public BigDecimal getOrderNotionalRatio() { return orderNotionalRatio; }
    public BigDecimal getProjectedExposureRatio() { return projectedExposureRatio; }
    public BigDecimal getProjectedRemainingCapitalRatio() { return projectedRemainingCapitalRatio; }
    public BigDecimal getDailyLossRatio() { return dailyLossRatio; }
    public List<PreTradeRiskViolation> getViolations() { return violations; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PreTradeRiskDecision that)) return false;
        return decisionStatus == that.decisionStatus
                && Objects.equals(clientOrderId, that.clientOrderId)
                && orderSide == that.orderSide
                && Objects.equals(orderQuantity, that.orderQuantity)
                && Objects.equals(referencePrice, that.referencePrice)
                && Objects.equals(orderNotional, that.orderNotional)
                && Objects.equals(estimatedFee, that.estimatedFee)
                && Objects.equals(currentPositionQuantity, that.currentPositionQuantity)
                && Objects.equals(projectedPositionQuantity, that.projectedPositionQuantity)
                && Objects.equals(currentPositionNotional, that.currentPositionNotional)
                && Objects.equals(projectedPositionNotional, that.projectedPositionNotional)
                && Objects.equals(totalEquity, that.totalEquity)
                && Objects.equals(projectedEquity, that.projectedEquity)
                && Objects.equals(availableCapital, that.availableCapital)
                && Objects.equals(projectedAvailableCapital, that.projectedAvailableCapital)
                && Objects.equals(orderNotionalRatio, that.orderNotionalRatio)
                && Objects.equals(projectedExposureRatio, that.projectedExposureRatio)
                && Objects.equals(projectedRemainingCapitalRatio, that.projectedRemainingCapitalRatio)
                && Objects.equals(dailyLossRatio, that.dailyLossRatio)
                && Objects.equals(violations, that.violations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                decisionStatus, clientOrderId, orderSide, orderQuantity, referencePrice, orderNotional,
                estimatedFee, currentPositionQuantity, projectedPositionQuantity, currentPositionNotional,
                projectedPositionNotional, totalEquity, projectedEquity, availableCapital,
                projectedAvailableCapital, orderNotionalRatio, projectedExposureRatio,
                projectedRemainingCapitalRatio, dailyLossRatio, violations);
    }
}
