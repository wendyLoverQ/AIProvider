package com.aiprovider.quant.portfolio.sizing;

import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import com.aiprovider.quant.market.model.PerpetualContract;
import java.math.BigDecimal;

public record MarketOrderQuantityRules(
        MarketProviderId provider,
        MarketType marketType,
        String symbol,
        String quoteAsset,
        int quantityPrecision,
        BigDecimal marketStepSize,
        BigDecimal marketMinQty,
        BigDecimal marketMaxQty,
        BigDecimal minNotional) {

    public MarketOrderQuantityRules {
        String validationFailure =
                validate(
                        provider,
                        marketType,
                        symbol,
                        quoteAsset,
                        quantityPrecision,
                        marketStepSize,
                        marketMinQty,
                        marketMaxQty,
                        minNotional);
        if (validationFailure != null) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_CONTRACT_RULES_INVALID,
                    validationFailure,
                    symbol);
        }
        if (marketType != MarketType.USDM_PERPETUAL) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_MARKET_NOT_SUPPORTED,
                    "MarketType must be USDM_PERPETUAL",
                    symbol);
        }
    }

    public static MarketOrderQuantityRules from(PerpetualContract contract) {
        if (contract == null) {
            throw failure(
                    PositionSizingException.POSITION_SIZING_CONTRACT_RULES_INVALID,
                    "PerpetualContract must not be null",
                    null);
        }
        return new MarketOrderQuantityRules(
                contract.getProvider(),
                contract.getMarketType(),
                contract.getSymbol(),
                contract.getQuoteAsset(),
                contract.getQuantityPrecision(),
                contract.getMarketStepSize(),
                contract.getMarketMinQty(),
                contract.getMarketMaxQty(),
                contract.getMinNotional());
    }

    private static String validate(
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            String quoteAsset,
            int quantityPrecision,
            BigDecimal marketStepSize,
            BigDecimal marketMinQty,
            BigDecimal marketMaxQty,
            BigDecimal minNotional) {
        if (provider == null) {
            return "Provider must not be null";
        }
        if (marketType == null) {
            return "MarketType must not be null";
        }
        if (symbol == null || symbol.isBlank()) {
            return "Symbol must not be blank";
        }
        if (quoteAsset == null || quoteAsset.isBlank()) {
            return "QuoteAsset must not be blank";
        }
        if (quantityPrecision < 0) {
            return "QuantityPrecision must not be negative";
        }
        if (!isPositive(marketStepSize)) {
            return "MarketStepSize must be greater than zero";
        }
        if (!isPositive(marketMinQty)) {
            return "MarketMinQty must be greater than zero";
        }
        if (!isPositive(marketMaxQty)) {
            return "MarketMaxQty must be greater than zero";
        }
        if (!isPositive(minNotional)) {
            return "MinNotional must be greater than zero";
        }
        if (marketMinQty.compareTo(marketMaxQty) > 0) {
            return "MarketMinQty must not exceed MarketMaxQty";
        }
        if (effectiveDecimalPlaces(marketStepSize) > quantityPrecision) {
            return "MarketStepSize decimal places must not exceed QuantityPrecision";
        }
        return null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static int effectiveDecimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    private static PositionSizingException failure(
            String errorCode, String reason, String symbol) {
        return new PositionSizingException(
                errorCode,
                reason
                        + " [Symbol="
                        + symbol
                        + ", PolicyType=null, TotalEquity=null, AvailableCapital=null"
                        + ", ReferencePrice=null, RawQuantity=null, NormalizedQuantity=null]");
    }
}
