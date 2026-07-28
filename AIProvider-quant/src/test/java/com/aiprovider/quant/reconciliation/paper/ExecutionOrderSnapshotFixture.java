package com.aiprovider.quant.execution.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ExecutionOrderSnapshotFixture {
    private ExecutionOrderSnapshotFixture() {
    }

    public static ExecutionOrderSnapshot copy(
            ExecutionOrderSnapshot source,
            ExecutionOrderStatus status,
            String executionOrderId,
            BigDecimal filledQuantity,
            BigDecimal remainingQuantity,
            BigDecimal averagePrice,
            BigDecimal cumulativeFee,
            String feeAsset,
            List<ExecutionFill> fills,
            Instant lastUpdatedAt,
            Instant completedAt) {
        return ExecutionOrderSnapshot.next(
                source,
                status,
                executionOrderId,
                filledQuantity,
                remainingQuantity,
                averagePrice,
                cumulativeFee,
                feeAsset,
                fills,
                source.getAcceptedAt(),
                source.getSubmittedAt(),
                lastUpdatedAt,
                completedAt,
                source.getTerminalErrorCode(),
                source.getTerminalErrorMessage());
    }
}
