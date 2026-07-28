package com.aiprovider.quant.portfolio.sizing;

import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DefaultPositionSizingEngine implements PositionSizingEngine {
    private static final int MINIMUM_DIVISION_SCALE = 34;
    private static final int EXPOSURE_RATIO_SCALE = 34;

    @Override
    public PositionSizingResult calculate(PositionSizingRequest request) {
        validateRequest(request);
        MarketOrderQuantityRules rules = request.marketOrderQuantityRules();
        validateRulesMatchRequest(request, rules);

        BigDecimal rawQuantity = null;
        BigDecimal normalizedQuantity = null;
        try {
            rawQuantity = calculateRawQuantity(request, rules);
            normalizedQuantity = normalize(rawQuantity, rules);
            if (normalizedQuantity.signum() == 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_QUANTITY_NORMALIZATION_ZERO,
                        "Quantity normalization produced zero",
                        request,
                        rawQuantity,
                        normalizedQuantity);
            }
            if (normalizedQuantity.compareTo(rules.marketMinQty()) < 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_QUANTITY_BELOW_MINIMUM,
                        "Normalized quantity is below MarketMinQty",
                        request,
                        rawQuantity,
                        normalizedQuantity);
            }
            if (normalizedQuantity.compareTo(rules.marketMaxQty()) > 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_QUANTITY_ABOVE_MAXIMUM,
                        "Normalized quantity is above MarketMaxQty",
                        request,
                        rawQuantity,
                        normalizedQuantity);
            }

            BigDecimal notional = normalizedQuantity.multiply(request.referencePrice());
            if (notional.compareTo(rules.minNotional()) < 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_NOTIONAL_BELOW_MINIMUM,
                        "Notional is below MinNotional",
                        request,
                        rawQuantity,
                        normalizedQuantity);
            }
            BigDecimal estimatedFee = notional.multiply(request.feeRate());
            BigDecimal requiredCapital = notional.add(estimatedFee);
            if (requiredCapital.compareTo(request.availableCapital()) > 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_CAPITAL_INSUFFICIENT,
                        "Required capital exceeds AvailableCapital",
                        request,
                        rawQuantity,
                        normalizedQuantity);
            }

            BigDecimal projectedPositionNotional =
                    request.currentPositionNotional().add(notional);
            BigDecimal projectedExposureRatio =
                    projectedPositionNotional.divide(
                            request.totalEquity(), EXPOSURE_RATIO_SCALE, RoundingMode.DOWN);
            return new PositionSizingResult(
                    request.policyType(),
                    rawQuantity,
                    normalizedQuantity,
                    rawQuantity.subtract(normalizedQuantity),
                    request.referencePrice(),
                    notional,
                    estimatedFee,
                    requiredCapital,
                    request.totalEquity(),
                    request.availableCapital(),
                    request.currentPositionNotional(),
                    projectedPositionNotional,
                    projectedExposureRatio,
                    rules.marketStepSize(),
                    rules.marketMinQty(),
                    rules.marketMaxQty(),
                    rules.minNotional(),
                    rules.quoteAsset());
        } catch (PositionSizingException error) {
            throw error;
        } catch (ArithmeticException error) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_CALCULATION_FAILED,
                    "Position sizing arithmetic failed",
                    request,
                    rawQuantity,
                    normalizedQuantity,
                    error);
        }
    }

    private BigDecimal calculateRawQuantity(
            PositionSizingRequest request, MarketOrderQuantityRules rules) {
        if (request.policyType() == PositionSizingPolicyType.FIXED_BASE_QUANTITY) {
            return request.fixedBaseQuantity();
        }
        if (request.policyType() == PositionSizingPolicyType.EQUITY_FRACTION) {
            BigDecimal capitalBudget =
                    request.totalEquity().multiply(request.equityFraction());
            if (request.availableCapital().compareTo(capitalBudget) < 0) {
                throw failure(
                        PositionSizingException.POSITION_SIZING_CAPITAL_INSUFFICIENT,
                        "Equity-fraction capital budget exceeds AvailableCapital",
                        request,
                        null,
                        null);
            }
            int divisionScale =
                    Math.max(MINIMUM_DIVISION_SCALE, rules.quantityPrecision() + 18);
            BigDecimal targetNotional =
                    capitalBudget.divide(
                            BigDecimal.ONE.add(request.feeRate()),
                            divisionScale,
                            RoundingMode.DOWN);
            return targetNotional.divide(
                    request.referencePrice(), divisionScale, RoundingMode.DOWN);
        }
        throw failure(
                PositionSizingException.POSITION_SIZING_POLICY_INVALID,
                "PolicyType is not supported",
                request,
                null,
                null);
    }

    private BigDecimal normalize(
            BigDecimal rawQuantity, MarketOrderQuantityRules rules) {
        BigDecimal stepCount =
                rawQuantity.divide(rules.marketStepSize(), 0, RoundingMode.DOWN);
        BigDecimal normalized = stepCount.multiply(rules.marketStepSize());
        return normalized
                .setScale(rules.quantityPrecision(), RoundingMode.DOWN)
                .stripTrailingZeros();
    }

    private void validateRequest(PositionSizingRequest request) {
        if (request == null) {
            throw new PositionSizingException(
                    PositionSizingException.POSITION_SIZING_REQUEST_INVALID,
                    "PositionSizingRequest must not be null"
                            + " [Symbol=null, PolicyType=null, TotalEquity=null"
                            + ", AvailableCapital=null, ReferencePrice=null"
                            + ", RawQuantity=null, NormalizedQuantity=null]");
        }
        if (request.provider() == null
                || request.marketType() == null
                || request.symbol() == null
                || request.symbol().isBlank()
                || request.positionSide() == null
                || request.totalEquity() == null
                || request.totalEquity().signum() <= 0
                || request.availableCapital() == null
                || request.availableCapital().signum() < 0
                || request.currentPositionNotional() == null
                || request.currentPositionNotional().signum() < 0
                || request.referencePrice() == null
                || request.referencePrice().signum() <= 0
                || request.feeRate() == null
                || request.feeRate().signum() < 0
                || request.leverage() == null
                || request.marketOrderQuantityRules() == null) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_REQUEST_INVALID,
                    "Position sizing request fields are incomplete or invalid",
                    request,
                    null,
                    null);
        }
        if (request.marketType() != MarketType.USDM_PERPETUAL) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_MARKET_NOT_SUPPORTED,
                    "MarketType must be USDM_PERPETUAL",
                    request,
                    null,
                    null);
        }
        if (request.positionSide() != PositionSide.LONG) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_REQUEST_INVALID,
                    "PositionSide must be LONG",
                    request,
                    null,
                    null);
        }
        if (request.leverage().compareTo(BigDecimal.ONE) != 0) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_LEVERAGE_NOT_SUPPORTED,
                    "Leverage must equal 1",
                    request,
                    null,
                    null);
        }
        validatePolicy(request);
    }

    private void validatePolicy(PositionSizingRequest request) {
        if (request.policyType() == null) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_POLICY_INVALID,
                    "PolicyType must not be null",
                    request,
                    null,
                    null);
        }
        if (request.policyType() == PositionSizingPolicyType.FIXED_BASE_QUANTITY
                && (request.fixedBaseQuantity() == null
                        || request.fixedBaseQuantity().signum() <= 0)) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_POLICY_INVALID,
                    "FixedBaseQuantity must be greater than zero",
                    request,
                    request.fixedBaseQuantity(),
                    null);
        }
        if (request.policyType() == PositionSizingPolicyType.EQUITY_FRACTION
                && (request.equityFraction() == null
                        || request.equityFraction().signum() <= 0
                        || request.equityFraction().compareTo(BigDecimal.ONE) > 0)) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_POLICY_INVALID,
                    "EquityFraction must be greater than zero and no greater than one",
                    request,
                    null,
                    null);
        }
    }

    private void validateRulesMatchRequest(
            PositionSizingRequest request, MarketOrderQuantityRules rules) {
        if (request.provider() != rules.provider()
                || request.marketType() != rules.marketType()
                || !request.symbol().equals(rules.symbol())) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_CONTRACT_RULES_INVALID,
                    "Market order quantity rules do not match the request market",
                    request,
                    null,
                    null);
        }
    }

    private PositionSizingException failure(
            String errorCode,
            String reason,
            PositionSizingRequest request,
            BigDecimal rawQuantity,
            BigDecimal normalizedQuantity) {
        return failure(errorCode, reason, request, rawQuantity, normalizedQuantity, null);
    }

    private PositionSizingException failure(
            String errorCode,
            String reason,
            PositionSizingRequest request,
            BigDecimal rawQuantity,
            BigDecimal normalizedQuantity,
            Throwable cause) {
        String message =
                reason
                        + " [Symbol="
                        + value(request == null ? null : request.symbol())
                        + ", PolicyType="
                        + value(request == null ? null : request.policyType())
                        + ", TotalEquity="
                        + value(request == null ? null : request.totalEquity())
                        + ", AvailableCapital="
                        + value(request == null ? null : request.availableCapital())
                        + ", ReferencePrice="
                        + value(request == null ? null : request.referencePrice())
                        + ", RawQuantity="
                        + value(rawQuantity)
                        + ", NormalizedQuantity="
                        + value(normalizedQuantity)
                        + "]";
        return cause == null
                ? new PositionSizingException(errorCode, message)
                : new PositionSizingException(errorCode, message, cause);
    }

    private String value(Object value) {
        return String.valueOf(value);
    }
}
