package com.aiprovider.quant.portfolio.sizing;

import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;
import java.math.BigDecimal;

public record PositionSizingRequest(
        MarketProviderId provider,
        MarketType marketType,
        String symbol,
        PositionSide positionSide,
        PositionSizingPolicyType policyType,
        BigDecimal totalEquity,
        BigDecimal availableCapital,
        BigDecimal currentPositionNotional,
        BigDecimal referencePrice,
        BigDecimal feeRate,
        BigDecimal leverage,
        BigDecimal fixedBaseQuantity,
        BigDecimal equityFraction,
        MarketOrderQuantityRules marketOrderQuantityRules) {}
