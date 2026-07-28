package com.aiprovider.quant.risk.pretrade;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

public final class DefaultPreTradeRiskEngine implements PreTradeRiskEngine {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    @Override
    public PreTradeRiskDecision evaluate(
            ExecutionOrderRequest request,
            PreTradeRiskContext context,
            PreTradeRiskPolicy policy) {
        validateInputs(request, context, policy);
        try {
            return calculate(request, context, policy);
        } catch (ArithmeticException exception) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_CALCULATION_FAILED",
                    "pre-trade risk calculation failed",
                    exception);
        }
    }

    private static void validateInputs(
            ExecutionOrderRequest request,
            PreTradeRiskContext context,
            PreTradeRiskPolicy policy) {
        if (request == null) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_REQUEST_INVALID",
                    "execution order request is required");
        }
        if (policy == null) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_POLICY_INVALID",
                    "pre-trade risk policy is required");
        }
        if (context == null) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_CONTEXT_INVALID",
                    "pre-trade risk context is required");
        }
        if (request.getMarketType() != MarketType.USDM_PERPETUAL
                || context.getMarketType() != MarketType.USDM_PERPETUAL) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_MARKET_NOT_SUPPORTED",
                    "only USDM_PERPETUAL is supported");
        }
        if (request.getProvider() != context.getProvider()
                || request.getMarketType() != context.getMarketType()
                || !request.getSymbol().equals(context.getSymbol())) {
            throw new PreTradeRiskException(
                    "PRE_TRADE_RISK_CONTEXT_MISMATCH",
                    "context provider, marketType and symbol must match the order");
        }
    }

    private static PreTradeRiskDecision calculate(
            ExecutionOrderRequest request,
            PreTradeRiskContext context,
            PreTradeRiskPolicy policy) {
        BigDecimal orderNotional = request.getQuantity().multiply(context.getReferencePrice());
        BigDecimal estimatedFee = orderNotional.multiply(context.getFeeRate());
        BigDecimal projectedEquity = context.getTotalEquity().subtract(estimatedFee);
        BigDecimal dailyLoss = context.getDailyRealizedPnl().signum() < 0
                ? context.getDailyRealizedPnl().negate()
                : BigDecimal.ZERO;
        BigDecimal orderNotionalRatio = divide(orderNotional, context.getTotalEquity());
        BigDecimal dailyLossRatio = divide(dailyLoss, context.getDayStartEquity());

        boolean entry = request.getOrderSide() == OrderSide.BUY;
        BigDecimal projectedPositionQuantity = entry
                ? context.getCurrentPositionQuantity().add(request.getQuantity())
                : context.getCurrentPositionQuantity().subtract(request.getQuantity());
        BigDecimal projectedPositionNotional = entry
                ? context.getCurrentPositionNotional().add(orderNotional)
                : projectedPositionQuantity.multiply(context.getReferencePrice());
        BigDecimal projectedAvailableCapital = entry
                ? context.getAvailableCapital().subtract(orderNotional).subtract(estimatedFee)
                : context.getAvailableCapital().add(orderNotional).subtract(estimatedFee);

        BigDecimal projectedExposureRatio;
        BigDecimal projectedRemainingCapitalRatio;
        if (projectedEquity.signum() == 0) {
            projectedExposureRatio = BigDecimal.ZERO;
            projectedRemainingCapitalRatio = BigDecimal.ZERO;
        } else {
            projectedExposureRatio = divide(projectedPositionNotional, projectedEquity);
            projectedRemainingCapitalRatio = divide(projectedAvailableCapital, projectedEquity);
        }

        List<PreTradeRiskViolation> violations = new ArrayList<>();
        if (entry) {
            evaluateEntryRules(
                    context, policy, orderNotionalRatio, projectedExposureRatio,
                    projectedAvailableCapital, projectedRemainingCapitalRatio,
                    dailyLossRatio, violations);
        } else {
            evaluateExitRules(request, context, violations);
        }
        if (projectedEquity.signum() <= 0
                && violations.stream().noneMatch(v -> v.getCode() == PreTradeRiskViolationCode.AVAILABLE_CAPITAL_INSUFFICIENT)) {
            add(
                    violations,
                    PreTradeRiskViolationCode.AVAILABLE_CAPITAL_INSUFFICIENT,
                    projectedEquity,
                    BigDecimal.ZERO,
                    "projected equity must remain greater than zero");
        }

        PreTradeRiskDecisionStatus status = violations.isEmpty()
                ? PreTradeRiskDecisionStatus.APPROVED
                : PreTradeRiskDecisionStatus.REJECTED;
        return new PreTradeRiskDecision(
                status,
                request.getClientOrderId(),
                request.getOrderSide(),
                request.getQuantity(),
                context.getReferencePrice(),
                orderNotional,
                estimatedFee,
                context.getCurrentPositionQuantity(),
                projectedPositionQuantity,
                context.getCurrentPositionNotional(),
                projectedPositionNotional,
                context.getTotalEquity(),
                projectedEquity,
                context.getAvailableCapital(),
                projectedAvailableCapital,
                orderNotionalRatio,
                projectedExposureRatio,
                projectedRemainingCapitalRatio,
                dailyLossRatio,
                violations);
    }

    private static void evaluateEntryRules(
            PreTradeRiskContext context,
            PreTradeRiskPolicy policy,
            BigDecimal orderNotionalRatio,
            BigDecimal projectedExposureRatio,
            BigDecimal projectedAvailableCapital,
            BigDecimal projectedRemainingCapitalRatio,
            BigDecimal dailyLossRatio,
            List<PreTradeRiskViolation> violations) {
        if (context.getCurrentPositionQuantity().signum() > 0
                || context.getCurrentPositionNotional().signum() > 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.ENTRY_POSITION_ALREADY_OPEN,
                    context.getCurrentPositionQuantity(),
                    BigDecimal.ZERO,
                    "a LONG position is already open");
        }
        if (projectedAvailableCapital.signum() < 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.AVAILABLE_CAPITAL_INSUFFICIENT,
                    projectedAvailableCapital,
                    BigDecimal.ZERO,
                    "projected available capital must not be negative");
        }
        if (orderNotionalRatio.compareTo(policy.getMaxOrderNotionalRatio()) > 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.ORDER_NOTIONAL_LIMIT_EXCEEDED,
                    orderNotionalRatio,
                    policy.getMaxOrderNotionalRatio(),
                    "order notional ratio exceeds the configured limit");
        }
        if (projectedExposureRatio.compareTo(policy.getMaxTotalExposureRatio()) > 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.TOTAL_EXPOSURE_LIMIT_EXCEEDED,
                    projectedExposureRatio,
                    policy.getMaxTotalExposureRatio(),
                    "projected exposure ratio exceeds the configured limit");
        }
        if (projectedRemainingCapitalRatio.compareTo(policy.getMinimumRemainingCapitalRatio()) < 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.REMAINING_CAPITAL_LIMIT_BREACHED,
                    projectedRemainingCapitalRatio,
                    policy.getMinimumRemainingCapitalRatio(),
                    "projected remaining capital ratio is below the configured minimum");
        }
        if (dailyLossRatio.compareTo(policy.getMaxDailyLossRatio()) >= 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.DAILY_LOSS_LIMIT_BREACHED,
                    dailyLossRatio,
                    policy.getMaxDailyLossRatio(),
                    "daily loss ratio has reached the configured limit");
        }
        if (context.getConsecutiveLosses() >= policy.getMaxConsecutiveLosses()) {
            add(
                    violations,
                    PreTradeRiskViolationCode.CONSECUTIVE_LOSS_LIMIT_BREACHED,
                    BigDecimal.valueOf(context.getConsecutiveLosses()),
                    BigDecimal.valueOf(policy.getMaxConsecutiveLosses()),
                    "consecutive losses have reached the configured limit");
        }
    }

    private static void evaluateExitRules(
            ExecutionOrderRequest request,
            PreTradeRiskContext context,
            List<PreTradeRiskViolation> violations) {
        if (context.getCurrentPositionQuantity().signum() == 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.EXIT_POSITION_NOT_OPEN,
                    context.getCurrentPositionQuantity(),
                    BigDecimal.ZERO,
                    "no LONG position is open");
        }
        if (request.getQuantity().compareTo(context.getCurrentPositionQuantity()) > 0) {
            add(
                    violations,
                    PreTradeRiskViolationCode.EXIT_QUANTITY_EXCEEDS_POSITION,
                    request.getQuantity(),
                    context.getCurrentPositionQuantity(),
                    "exit quantity exceeds the current position quantity");
        }
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, CALCULATION_CONTEXT);
    }

    private static void add(
            List<PreTradeRiskViolation> violations,
            PreTradeRiskViolationCode code,
            BigDecimal actualValue,
            BigDecimal limitValue,
            String message) {
        violations.add(new PreTradeRiskViolation(code, actualValue, limitValue, message));
    }
}
