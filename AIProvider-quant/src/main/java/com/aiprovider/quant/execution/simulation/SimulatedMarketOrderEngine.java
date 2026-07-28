package com.aiprovider.quant.execution.simulation;

import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;

import java.time.Instant;

public interface SimulatedMarketOrderEngine {
    ExecutionOrderSnapshot submit(ExecutionOrderSnapshot acceptedOrderSnapshot, Instant submittedAt);

    SimulatedExecutionResult execute(ExecutionOrderSnapshot submittedOrPartialSnapshot,
                                     SimulatedTopOfBook topOfBook,
                                     SimulatedExecutionPolicy policy);
}
