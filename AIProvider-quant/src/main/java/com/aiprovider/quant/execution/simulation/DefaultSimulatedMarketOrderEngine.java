package com.aiprovider.quant.execution.simulation;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderException;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStateMachine;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;
import com.aiprovider.quant.execution.order.ExecutionOrderType;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deterministic MARKET order simulator consuming one immutable top-of-book snapshot per execution step.
 *
 * <p>This stage intentionally does not normalize prices to TickSize or pretend to apply exchange-specific
 * price precision.</p>
 */
public final class DefaultSimulatedMarketOrderEngine implements SimulatedMarketOrderEngine {
    private static final BigDecimal BPS_DENOMINATOR = new BigDecimal("10000");

    private final ExecutionOrderStateMachine stateMachine;

    public DefaultSimulatedMarketOrderEngine() {
        this(new ExecutionOrderStateMachine());
    }

    public DefaultSimulatedMarketOrderEngine(ExecutionOrderStateMachine stateMachine) {
        if (stateMachine == null) {
            throw error("SIMULATED_EXECUTION_REQUEST_INVALID", "stateMachine is null");
        }
        this.stateMachine = stateMachine;
    }

    @Override
    public ExecutionOrderSnapshot submit(ExecutionOrderSnapshot acceptedOrderSnapshot, Instant submittedAt) {
        if (acceptedOrderSnapshot == null || submittedAt == null) {
            throw error("SIMULATED_EXECUTION_REQUEST_INVALID", "acceptedOrderSnapshot and submittedAt are required");
        }
        requireStatus(acceptedOrderSnapshot, ExecutionOrderStatus.ACCEPTED);
        ExecutionOrderRequest request = acceptedOrderSnapshot.getRequest();
        requireSupportedRequest(request);
        if (acceptedOrderSnapshot.getAcceptedAt() == null
                || submittedAt.isBefore(acceptedOrderSnapshot.getAcceptedAt())) {
            throw error("SIMULATED_EXECUTION_TIME_INVALID", "submittedAt precedes acceptedAt");
        }
        String executionOrderId = "SIM-ORDER:" + request.getClientOrderId();
        try {
            return stateMachine.submit(acceptedOrderSnapshot, executionOrderId, submittedAt);
        } catch (ExecutionOrderException exception) {
            throw error("SIMULATED_EXECUTION_CALCULATION_FAILED",
                    "order state machine rejected submit: " + exception.getErrorCode());
        }
    }

    @Override
    public SimulatedExecutionResult execute(ExecutionOrderSnapshot snapshot, SimulatedTopOfBook topOfBook,
                                            SimulatedExecutionPolicy policy) {
        if (snapshot == null || topOfBook == null || policy == null) {
            throw error("SIMULATED_EXECUTION_REQUEST_INVALID", "snapshot, topOfBook and policy are required");
        }
        requireStatus(snapshot, ExecutionOrderStatus.SUBMITTED, ExecutionOrderStatus.PARTIALLY_FILLED);
        ExecutionOrderRequest request = snapshot.getRequest();
        if (topOfBook.getMarketType() != MarketType.USDM_PERPETUAL) {
            throw error("SIMULATED_EXECUTION_MARKET_INVALID", "only USDM_PERPETUAL is supported");
        }
        requireMatchingContext(request, topOfBook);
        requireSupportedRequest(request);
        Instant previousFillOrSubmitTime = snapshot.getFills().isEmpty()
                ? snapshot.getSubmittedAt()
                : snapshot.getFills().get(snapshot.getFills().size() - 1).getFilledAt();
        if (previousFillOrSubmitTime == null || !topOfBook.getEventTime().isAfter(previousFillOrSubmitTime)) {
            throw error("SIMULATED_EXECUTION_TIME_INVALID",
                    "top-of-book eventTime must be strictly later than the previous order event");
        }

        OrderSide side = request.getOrderSide();
        BigDecimal originalPrice = side == OrderSide.BUY ? topOfBook.getAskPrice() : topOfBook.getBidPrice();
        BigDecimal availableQuantity = side == OrderSide.BUY
                ? topOfBook.getAskQuantity()
                : topOfBook.getBidQuantity();
        if (availableQuantity.signum() <= 0) {
            throw error("SIMULATED_EXECUTION_LIQUIDITY_INVALID", "available top quantity is not positive");
        }
        if (snapshot.getRemainingQuantity() == null || snapshot.getRemainingQuantity().signum() <= 0) {
            throw error("SIMULATED_EXECUTION_LIQUIDITY_INVALID", "order remaining quantity is not positive");
        }

        try {
            BigDecimal slippageRatio = policy.getSlippageBps().divide(BPS_DENOMINATOR);
            BigDecimal priceFactor = side == OrderSide.BUY
                    ? BigDecimal.ONE.add(slippageRatio)
                    : BigDecimal.ONE.subtract(slippageRatio);
            BigDecimal fillPrice = originalPrice.multiply(priceFactor);
            if (fillPrice.signum() <= 0) {
                throw error("SIMULATED_EXECUTION_PRICE_INVALID", "calculated fill price is not positive");
            }
            BigDecimal fillQuantity = snapshot.getRemainingQuantity().min(availableQuantity);
            if (fillQuantity.signum() <= 0) {
                throw error("SIMULATED_EXECUTION_LIQUIDITY_INVALID", "calculated fill quantity is not positive");
            }
            BigDecimal fee = fillQuantity.multiply(fillPrice).multiply(policy.getFeeRate());
            String fillId = "SIM-FILL:" + snapshot.getExecutionOrderId() + ":" + snapshot.getFills().size()
                    + ":" + topOfBook.getEventTime().toEpochMilli();
            ExecutionFill fill = new ExecutionFill(fillId, fillQuantity, fillPrice, fee,
                    policy.getFeeAsset(), topOfBook.getEventTime());
            ExecutionOrderSnapshot updated = applyFill(snapshot, fill);
            return new SimulatedExecutionResult(updated, fill, side, topOfBook.getBidPrice(),
                    topOfBook.getAskPrice(), policy.getSlippageBps(), availableQuantity, fillQuantity,
                    updated.getRemainingQuantity(), updated.getStatus() == ExecutionOrderStatus.FILLED);
        } catch (ArithmeticException exception) {
            throw error("SIMULATED_EXECUTION_CALCULATION_FAILED",
                    "decimal calculation failed: " + exception.getClass().getSimpleName());
        }
    }

    private ExecutionOrderSnapshot applyFill(ExecutionOrderSnapshot snapshot, ExecutionFill fill) {
        try {
            return stateMachine.applyFill(snapshot, fill);
        } catch (ExecutionOrderException exception) {
            throw error("SIMULATED_EXECUTION_CALCULATION_FAILED",
                    "order state machine rejected fill: " + exception.getErrorCode());
        }
    }

    private void requireSupportedRequest(ExecutionOrderRequest request) {
        if (request == null || request.getOrderType() != ExecutionOrderType.MARKET
                || request.getPositionSide() != PositionSide.LONG) {
            throw error("SIMULATED_EXECUTION_REQUEST_INVALID", "only MARKET LONG orders are supported");
        }
        if (request.getMarketType() != MarketType.USDM_PERPETUAL) {
            throw error("SIMULATED_EXECUTION_MARKET_INVALID", "only USDM_PERPETUAL is supported");
        }
    }

    private void requireMatchingContext(ExecutionOrderRequest request, SimulatedTopOfBook topOfBook) {
        if (request.getProvider() != topOfBook.getProvider()
                || request.getMarketType() != topOfBook.getMarketType()
                || !request.getSymbol().equals(topOfBook.getSymbol())) {
            throw error("SIMULATED_EXECUTION_CONTEXT_MISMATCH",
                    "order and top-of-book provider, marketType or symbol differ");
        }
    }

    private void requireStatus(ExecutionOrderSnapshot snapshot, ExecutionOrderStatus... allowed) {
        for (ExecutionOrderStatus status : allowed) {
            if (snapshot.getStatus() == status) return;
        }
        throw error("SIMULATED_EXECUTION_STATUS_INVALID", "status=" + snapshot.getStatus());
    }

    private static SimulatedExecutionException error(String code, String message) {
        return new SimulatedExecutionException(code, message);
    }
}
