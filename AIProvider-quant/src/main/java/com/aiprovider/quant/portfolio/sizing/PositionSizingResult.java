package com.aiprovider.quant.portfolio.sizing;

import java.math.BigDecimal;
import java.util.Objects;

public record PositionSizingResult(
        PositionSizingPolicyType policyType,
        BigDecimal rawQuantity,
        BigDecimal normalizedQuantity,
        BigDecimal quantityReduction,
        BigDecimal referencePrice,
        BigDecimal notional,
        BigDecimal estimatedFee,
        BigDecimal requiredCapital,
        BigDecimal totalEquity,
        BigDecimal availableCapital,
        BigDecimal currentPositionNotional,
        BigDecimal projectedPositionNotional,
        BigDecimal projectedExposureRatio,
        BigDecimal marketStepSize,
        BigDecimal marketMinQty,
        BigDecimal marketMaxQty,
        BigDecimal minNotional,
        String quoteAsset) {

    public PositionSizingResult {
        Objects.requireNonNull(policyType, "policyType");
        Objects.requireNonNull(rawQuantity, "rawQuantity");
        Objects.requireNonNull(normalizedQuantity, "normalizedQuantity");
        Objects.requireNonNull(quantityReduction, "quantityReduction");
        Objects.requireNonNull(referencePrice, "referencePrice");
        Objects.requireNonNull(notional, "notional");
        Objects.requireNonNull(estimatedFee, "estimatedFee");
        Objects.requireNonNull(requiredCapital, "requiredCapital");
        Objects.requireNonNull(totalEquity, "totalEquity");
        Objects.requireNonNull(availableCapital, "availableCapital");
        Objects.requireNonNull(currentPositionNotional, "currentPositionNotional");
        Objects.requireNonNull(projectedPositionNotional, "projectedPositionNotional");
        Objects.requireNonNull(projectedExposureRatio, "projectedExposureRatio");
        Objects.requireNonNull(marketStepSize, "marketStepSize");
        Objects.requireNonNull(marketMinQty, "marketMinQty");
        Objects.requireNonNull(marketMaxQty, "marketMaxQty");
        Objects.requireNonNull(minNotional, "minNotional");
        Objects.requireNonNull(quoteAsset, "quoteAsset");
    }
}
