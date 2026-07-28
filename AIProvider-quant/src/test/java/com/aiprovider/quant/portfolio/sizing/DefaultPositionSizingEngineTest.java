package com.aiprovider.quant.portfolio.sizing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DefaultPositionSizingEngineTest {
    private final DefaultPositionSizingEngine engine = new DefaultPositionSizingEngine();

    @Test
    void fixedBaseQuantityReturnsCompleteResultWhenRulesAreSatisfied() {
        PositionSizingResult result =
                engine.calculate(
                        request(
                                PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                                "0.010",
                                null,
                                "1000",
                                "1000",
                                "100",
                                "20000",
                                "0.001",
                                "1",
                                rules("0.001", "0.001", "10", "5")));

        assertThat(result.policyType())
                .isEqualTo(PositionSizingPolicyType.FIXED_BASE_QUANTITY);
        assertThat(result.rawQuantity()).isEqualByComparingTo("0.010");
        assertThat(result.normalizedQuantity()).isEqualByComparingTo("0.01");
        assertThat(result.quantityReduction()).isEqualByComparingTo("0");
        assertThat(result.notional()).isEqualByComparingTo("200");
        assertThat(result.estimatedFee()).isEqualByComparingTo("0.200");
        assertThat(result.requiredCapital()).isEqualByComparingTo("200.200");
        assertThat(result.projectedPositionNotional()).isEqualByComparingTo("300");
        assertThat(result.projectedExposureRatio()).isEqualByComparingTo("0.3");
        assertThat(result.quoteAsset()).isEqualTo("USDT");
    }

    @Test
    void equityFractionDeductsEstimatedFeeBeforeCalculatingQuantity() {
        PositionSizingResult result =
                engine.calculate(
                        request(
                                PositionSizingPolicyType.EQUITY_FRACTION,
                                null,
                                "0.2",
                                "1000",
                                "1000",
                                "0",
                                "100",
                                "0.01",
                                "1",
                                rules("0.01", "0.01", "100", "5")));

        assertThat(result.rawQuantity())
                .isEqualByComparingTo("1.9801980198019801980198019801980198");
        assertThat(result.normalizedQuantity()).isEqualByComparingTo("1.98");
        assertThat(result.notional()).isEqualByComparingTo("198");
        assertThat(result.estimatedFee()).isEqualByComparingTo("1.98");
        assertThat(result.requiredCapital()).isEqualByComparingTo("199.98");
    }

    @Test
    void quantityIsAlwaysRoundedDownByMarketStepSize() {
        PositionSizingResult result =
                engine.calculate(
                        request(
                                PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                                "1.2399",
                                null,
                                "1000",
                                "1000",
                                "0",
                                "100",
                                "0",
                                "1",
                                rules("0.005", "0.005", "100", "5")));

        assertThat(result.normalizedQuantity()).isEqualByComparingTo("1.235");
        assertThat(result.quantityReduction()).isEqualByComparingTo("0.0049");
    }

    @Test
    void quantityBelowMinimumFailsWithoutIncreasingIt() {
        assertFailure(
                request(
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        "0.009",
                        null,
                        "1000",
                        "1000",
                        "0",
                        "1000",
                        "0",
                        "1",
                        rules("0.001", "0.01", "100", "5")),
                PositionSizingException.POSITION_SIZING_QUANTITY_BELOW_MINIMUM);
    }

    @Test
    void quantityAboveMaximumFails() {
        assertFailure(
                request(
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        "10.001",
                        null,
                        "100000",
                        "100000",
                        "0",
                        "100",
                        "0",
                        "1",
                        rules("0.001", "0.001", "10", "5")),
                PositionSizingException.POSITION_SIZING_QUANTITY_ABOVE_MAXIMUM);
    }

    @Test
    void notionalBelowMinimumFails() {
        assertFailure(
                request(
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        "0.01",
                        null,
                        "1000",
                        "1000",
                        "0",
                        "100",
                        "0",
                        "1",
                        rules("0.001", "0.001", "10", "5")),
                PositionSizingException.POSITION_SIZING_NOTIONAL_BELOW_MINIMUM);
    }

    @Test
    void fixedQuantityFailsWhenRequiredCapitalExceedsAvailableCapital() {
        assertFailure(
                request(
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        "1",
                        null,
                        "1000",
                        "100",
                        "0",
                        "100",
                        "0.01",
                        "1",
                        rules("0.001", "0.001", "10", "5")),
                PositionSizingException.POSITION_SIZING_CAPITAL_INSUFFICIENT);
    }

    @Test
    void equityFractionBudgetFailsWithoutShrinkingToAvailableCapital() {
        assertFailure(
                request(
                        PositionSizingPolicyType.EQUITY_FRACTION,
                        null,
                        "0.2",
                        "1000",
                        "199.99",
                        "0",
                        "100",
                        "0.01",
                        "1",
                        rules("0.001", "0.001", "10", "5")),
                PositionSizingException.POSITION_SIZING_CAPITAL_INSUFFICIENT);
    }

    @Test
    void leverageOtherThanOneFails() {
        assertFailure(
                request(
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        "1",
                        null,
                        "1000",
                        "1000",
                        "0",
                        "100",
                        "0",
                        "2",
                        rules("0.001", "0.001", "10", "5")),
                PositionSizingException.POSITION_SIZING_LEVERAGE_NOT_SUPPORTED);
    }

    @Test
    void missingMarketTypeFailsInsteadOfBeingTreatedAsUsdmPerpetual() {
        PositionSizingRequest request =
                new PositionSizingRequest(
                        MarketProviderId.BINANCE_USDM,
                        null,
                        "BTCUSDT",
                        PositionSide.LONG,
                        PositionSizingPolicyType.FIXED_BASE_QUANTITY,
                        new BigDecimal("1000"),
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        new BigDecimal("100"),
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        null,
                        rules("0.001", "0.001", "10", "5"));

        assertFailure(request, PositionSizingException.POSITION_SIZING_REQUEST_INVALID);
    }

    @Test
    void rulesAreUnaffectedWhenOriginalContractChanges() {
        PerpetualContract contract = contract();
        MarketOrderQuantityRules snapshot = MarketOrderQuantityRules.from(contract);

        contract.setSymbol("ETHUSDT");
        contract.setMarketStepSize(new BigDecimal("1"));
        contract.setMarketMinQty(new BigDecimal("2"));

        assertThat(snapshot.symbol()).isEqualTo("BTCUSDT");
        assertThat(snapshot.marketStepSize()).isEqualByComparingTo("0.001");
        assertThat(snapshot.marketMinQty()).isEqualByComparingTo("0.001");
    }

    @Test
    void sameInputAlwaysProducesExactlyEqualResults() {
        PositionSizingRequest request =
                request(
                        PositionSizingPolicyType.EQUITY_FRACTION,
                        null,
                        "0.333",
                        "1234.56",
                        "1000",
                        "50",
                        "27123.45",
                        "0.0004",
                        "1",
                        rules("0.001", "0.001", "10", "5"));

        assertThat(engine.calculate(request)).isEqualTo(engine.calculate(request));
    }

    @Test
    void invalidContractRulesFailWithoutUsingLimitOrderFallbacks() {
        PerpetualContract contract = contract();
        contract.setMarketStepSize(null);
        contract.setStepSize(new BigDecimal("0.001"));

        assertThatThrownBy(() -> MarketOrderQuantityRules.from(contract))
                .isInstanceOf(PositionSizingException.class)
                .extracting(error -> ((PositionSizingException) error).getErrorCode())
                .isEqualTo(
                        PositionSizingException.POSITION_SIZING_CONTRACT_RULES_INVALID);
    }

    private void assertFailure(PositionSizingRequest request, String errorCode) {
        assertThatThrownBy(() -> engine.calculate(request))
                .isInstanceOf(PositionSizingException.class)
                .satisfies(
                        error -> {
                            PositionSizingException sizingError =
                                    (PositionSizingException) error;
                            assertThat(sizingError.getErrorCode()).isEqualTo(errorCode);
                            assertThat(sizingError.getMessage())
                                    .contains(
                                            "Symbol=",
                                            "PolicyType=",
                                            "TotalEquity=",
                                            "AvailableCapital=",
                                            "ReferencePrice=",
                                            "RawQuantity=",
                                            "NormalizedQuantity=");
                        });
    }

    private PositionSizingRequest request(
            PositionSizingPolicyType policyType,
            String fixedBaseQuantity,
            String equityFraction,
            String totalEquity,
            String availableCapital,
            String currentPositionNotional,
            String referencePrice,
            String feeRate,
            String leverage,
            MarketOrderQuantityRules rules) {
        return new PositionSizingRequest(
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                "BTCUSDT",
                PositionSide.LONG,
                policyType,
                decimal(totalEquity),
                decimal(availableCapital),
                decimal(currentPositionNotional),
                decimal(referencePrice),
                decimal(feeRate),
                decimal(leverage),
                decimal(fixedBaseQuantity),
                decimal(equityFraction),
                rules);
    }

    private MarketOrderQuantityRules rules(
            String stepSize, String minQty, String maxQty, String minNotional) {
        return new MarketOrderQuantityRules(
                MarketProviderId.BINANCE_USDM,
                MarketType.USDM_PERPETUAL,
                "BTCUSDT",
                "USDT",
                3,
                decimal(stepSize),
                decimal(minQty),
                decimal(maxQty),
                decimal(minNotional));
    }

    private PerpetualContract contract() {
        PerpetualContract contract = new PerpetualContract();
        contract.setProvider(MarketProviderId.BINANCE_USDM);
        contract.setMarketType(MarketType.USDM_PERPETUAL);
        contract.setSymbol("BTCUSDT");
        contract.setQuoteAsset("USDT");
        contract.setQuantityPrecision(3);
        contract.setMarketStepSize(new BigDecimal("0.001"));
        contract.setMarketMinQty(new BigDecimal("0.001"));
        contract.setMarketMaxQty(new BigDecimal("10"));
        contract.setMinNotional(new BigDecimal("5"));
        return contract;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
