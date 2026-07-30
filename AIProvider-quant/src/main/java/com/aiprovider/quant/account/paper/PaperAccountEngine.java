package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface PaperAccountEngine {
    default PaperAccountSnapshot restore(PaperAccountRestoreRequest request) {
        throw new UnsupportedOperationException("restore is not implemented by this engine");
    }

    PaperAccountSnapshot initialize(
            String accountId,
            MarketProviderId provider,
            MarketType marketType,
            String quoteAsset,
            BigDecimal initialCapital,
            LocalDate initialUtcDate,
            Instant initializedAt);

    PaperAccountUpdateResult applyFill(
            PaperAccountSnapshot account,
            ExecutionOrderRequest orderRequest,
            ExecutionFill fill);

    PaperAccountSnapshot markToMarket(
            PaperAccountSnapshot account, String symbol, BigDecimal markPrice, Instant markedAt);

    PaperAccountSnapshot rollUtcTradingDay(
            PaperAccountSnapshot account, LocalDate nextUtcDate, Instant rolledAt);
}
