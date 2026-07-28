package com.aiprovider.quant.risk.pretrade;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPreTradeRiskEngineTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-28T00:00:00Z");
    private static final DefaultPreTradeRiskEngine ENGINE = new DefaultPreTradeRiskEngine();

    @Test
    void approvesValidBuyWhenNoPositionIsOpen() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("2"),
                context("BTCUSDT", "100", "0.001", "1000", "1000", "0", "0", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertThat(decision.getDecisionStatus()).isEqualTo(PreTradeRiskDecisionStatus.APPROVED);
        assertThat(decision.getViolations()).isEmpty();
        assertThat(decision.getOrderNotional()).isEqualByComparingTo("200");
        assertThat(decision.getEstimatedFee()).isEqualByComparingTo("0.2");
        assertThat(decision.getProjectedPositionQuantity()).isEqualByComparingTo("2");
        assertThat(decision.getProjectedAvailableCapital()).isEqualByComparingTo("799.8");
    }

    @Test
    void rejectsBuyWhenPositionIsAlreadyOpen() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("1"),
                context("BTCUSDT", "100", "0.001", "1000", "800", "2", "200", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.ENTRY_POSITION_ALREADY_OPEN);
    }

    @Test
    void rejectsBuyWhenOrderNotionalRatioExceedsLimit() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("4"),
                context("BTCUSDT", "100", "0", "1000", "1000", "0", "0", "1000", "0", 0),
                policy("0.3", "0.8", "0", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.ORDER_NOTIONAL_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsBuyWhenProjectedExposureExceedsLimit() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("6"),
                context("BTCUSDT", "100", "0.001", "1000", "1000", "0", "0", "1000", "0", 0),
                policy("0.8", "0.5", "0", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.TOTAL_EXPOSURE_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsInsufficientAvailableCapitalWithoutReducingQuantity() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("6"),
                context("BTCUSDT", "100", "0.001", "1000", "500", "0", "0", "1000", "0", 0),
                policy("0.8", "1", "0", "0.1", 3));

        assertCodes(
                decision,
                PreTradeRiskViolationCode.AVAILABLE_CAPITAL_INSUFFICIENT,
                PreTradeRiskViolationCode.REMAINING_CAPITAL_LIMIT_BREACHED);
        assertThat(decision.getOrderQuantity()).isEqualByComparingTo("6");
        assertThat(decision.getProjectedAvailableCapital()).isNegative();
    }

    @Test
    void rejectsBuyWhenRemainingCapitalRatioIsBelowMinimum() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("5"),
                context("BTCUSDT", "100", "0.001", "1000", "600", "0", "0", "1000", "0", 0),
                policy("0.8", "0.8", "0.2", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.REMAINING_CAPITAL_LIMIT_BREACHED);
    }

    @Test
    void rejectsBuyWhenDailyLossReachesLimit() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("1"),
                context("BTCUSDT", "100", "0", "1000", "1000", "0", "0", "1000", "-100", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.DAILY_LOSS_LIMIT_BREACHED);
    }

    @Test
    void rejectsBuyWhenConsecutiveLossesReachLimit() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("1"),
                context("BTCUSDT", "100", "0", "1000", "1000", "0", "0", "1000", "0", 3),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.CONSECUTIVE_LOSS_LIMIT_BREACHED);
    }

    @Test
    void returnsEveryViolationOnceInStableRequiredOrder() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                buy("9"),
                context("BTCUSDT", "100", "0.001", "1000", "100", "2", "200", "1000", "-200", 3),
                policy("0.5", "0.5", "0.2", "0.1", 3));

        assertCodes(
                decision,
                PreTradeRiskViolationCode.ENTRY_POSITION_ALREADY_OPEN,
                PreTradeRiskViolationCode.AVAILABLE_CAPITAL_INSUFFICIENT,
                PreTradeRiskViolationCode.ORDER_NOTIONAL_LIMIT_EXCEEDED,
                PreTradeRiskViolationCode.TOTAL_EXPOSURE_LIMIT_EXCEEDED,
                PreTradeRiskViolationCode.REMAINING_CAPITAL_LIMIT_BREACHED,
                PreTradeRiskViolationCode.DAILY_LOSS_LIMIT_BREACHED,
                PreTradeRiskViolationCode.CONSECUTIVE_LOSS_LIMIT_BREACHED);
        assertThat(decision.getViolations()).extracting(PreTradeRiskViolation::getCode).doesNotHaveDuplicates();
    }

    @Test
    void approvesValidSellEvenWhenEntryLossRulesAreBreached() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                sell("2"),
                context("BTCUSDT", "100", "0.001", "1000", "500", "5", "500", "1000", "-200", 5),
                policy("0.1", "0.1", "0.9", "0.1", 3));

        assertThat(decision.getDecisionStatus()).isEqualTo(PreTradeRiskDecisionStatus.APPROVED);
        assertThat(decision.getViolations()).isEmpty();
    }

    @Test
    void approvesValidPartialExit() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                sell("2"),
                context("BTCUSDT", "100", "0.001", "1000", "500", "5", "500", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertThat(decision.getDecisionStatus()).isEqualTo(PreTradeRiskDecisionStatus.APPROVED);
        assertThat(decision.getProjectedPositionQuantity()).isEqualByComparingTo("3");
        assertThat(decision.getProjectedPositionNotional()).isEqualByComparingTo("300");
    }

    @Test
    void rejectsExitQuantityThatExceedsPosition() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                sell("6"),
                context("BTCUSDT", "100", "0", "1000", "500", "5", "500", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertCodes(decision, PreTradeRiskViolationCode.EXIT_QUANTITY_EXCEEDS_POSITION);
    }

    @Test
    void rejectsSellWhenNoPositionIsOpen() {
        PreTradeRiskDecision decision = ENGINE.evaluate(
                sell("1"),
                context("BTCUSDT", "100", "0", "1000", "1000", "0", "0", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3));

        assertCodes(
                decision,
                PreTradeRiskViolationCode.EXIT_POSITION_NOT_OPEN,
                PreTradeRiskViolationCode.EXIT_QUANTITY_EXCEEDS_POSITION);
    }

    @Test
    void throwsExplicitExceptionWhenContextSymbolDoesNotMatchOrder() {
        assertThatThrownBy(() -> ENGINE.evaluate(
                buy("1"),
                context("ETHUSDT", "100", "0", "1000", "1000", "0", "0", "1000", "0", 0),
                policy("0.5", "0.5", "0.1", "0.1", 3)))
                .isInstanceOf(PreTradeRiskException.class)
                .extracting("errorCode")
                .isEqualTo("PRE_TRADE_RISK_CONTEXT_MISMATCH");
    }

    @Test
    void identicalInputsProduceIdenticalDecisions() {
        ExecutionOrderRequest request = buy("2");
        PreTradeRiskContext context =
                context("BTCUSDT", "100", "0.001", "1000", "1000", "0", "0", "1000", "0", 0);
        PreTradeRiskPolicy policy = policy("0.5", "0.5", "0.1", "0.1", 3);

        PreTradeRiskDecision first = ENGINE.evaluate(request, context, policy);
        PreTradeRiskDecision second = ENGINE.evaluate(request, context, policy);

        assertThat(second).isEqualTo(first);
        assertThat(second.hashCode()).isEqualTo(first.hashCode());
    }

    @Test
    void policyAndContextRejectInvalidStructuresWithStableErrorCodes() {
        assertThatThrownBy(() -> policy("0", "0.5", "0.1", "0.1", 3))
                .isInstanceOf(PreTradeRiskException.class)
                .extracting("errorCode")
                .isEqualTo("PRE_TRADE_RISK_POLICY_INVALID");
        assertThatThrownBy(() ->
                context("BTCUSDT", "100", "0", "1000", "1000", "1", "0", "1000", "0", 0))
                .isInstanceOf(PreTradeRiskException.class)
                .extracting("errorCode")
                .isEqualTo("PRE_TRADE_RISK_CONTEXT_INVALID");
    }

    private static void assertCodes(
            PreTradeRiskDecision decision,
            PreTradeRiskViolationCode... expectedCodes) {
        assertThat(decision.getDecisionStatus()).isEqualTo(PreTradeRiskDecisionStatus.REJECTED);
        assertThat(decision.getViolations())
                .extracting(PreTradeRiskViolation::getCode)
                .containsExactly(expectedCodes);
    }

    private static ExecutionOrderRequest buy(String quantity) {
        return request(quantity, OrderSide.BUY, false);
    }

    private static ExecutionOrderRequest sell(String quantity) {
        return request(quantity, OrderSide.SELL, true);
    }

    private static ExecutionOrderRequest request(String quantity, OrderSide orderSide, boolean reduceOnly) {
        return new ExecutionOrderRequest(
                "client-order-1",
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                "BTCUSDT",
                ExecutionOrderType.MARKET,
                orderSide,
                PositionSide.LONG,
                decimal(quantity),
                reduceOnly,
                REQUESTED_AT);
    }

    private static PreTradeRiskPolicy policy(
            String maxOrderNotionalRatio,
            String maxTotalExposureRatio,
            String minimumRemainingCapitalRatio,
            String maxDailyLossRatio,
            int maxConsecutiveLosses) {
        return new PreTradeRiskPolicy(
                decimal(maxOrderNotionalRatio),
                decimal(maxTotalExposureRatio),
                decimal(minimumRemainingCapitalRatio),
                decimal(maxDailyLossRatio),
                maxConsecutiveLosses);
    }

    private static PreTradeRiskContext context(
            String symbol,
            String referencePrice,
            String feeRate,
            String totalEquity,
            String availableCapital,
            String currentPositionQuantity,
            String currentPositionNotional,
            String dayStartEquity,
            String dailyRealizedPnl,
            int consecutiveLosses) {
        return new PreTradeRiskContext(
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                symbol,
                decimal(referencePrice),
                decimal(feeRate),
                decimal(totalEquity),
                decimal(availableCapital),
                decimal(currentPositionQuantity),
                decimal(currentPositionNotional),
                decimal(dayStartEquity),
                decimal(dailyRealizedPnl),
                consecutiveLosses);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
