package com.aiprovider.quant.account.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaperAccountSnapshotFixture {
    private PaperAccountSnapshotFixture() {
    }

    public static PaperAccountSnapshot copy(
            PaperAccountSnapshot source,
            PaperPositionSnapshot position,
            List<PaperAppliedFill> appliedFills,
            Instant lastUpdatedAt) {
        return new PaperAccountSnapshot(
                source.getAccountId(),
                source.getProvider(),
                source.getMarketType(),
                source.getQuoteAsset(),
                source.getInitialCapital(),
                source.getRealizedPnl(),
                source.getUnrealizedPnl(),
                source.getTotalEquity(),
                source.getAvailableCapital(),
                position,
                source.getTradingDayState(),
                source.getConsecutiveLosses(),
                appliedFills,
                lastUpdatedAt);
    }

    public static PaperPositionSnapshot openPosition(
            String symbol,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            String openingClientOrderId) {
        return PaperPositionSnapshot.open(
                symbol,
                quantity,
                averageEntryPrice,
                averageEntryPrice,
                quantity.multiply(averageEntryPrice),
                BigDecimal.ZERO,
                openingClientOrderId,
                BigDecimal.ZERO);
    }
}
