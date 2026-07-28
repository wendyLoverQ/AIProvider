package com.aiprovider.quant.execution.order;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class ExecutionOrderRequest {
    private final String clientOrderId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final ExecutionOrderType orderType;
    private final OrderSide orderSide;
    private final PositionSide positionSide;
    private final BigDecimal quantity;
    private final boolean reduceOnly;
    private final Instant requestedAt;

    public ExecutionOrderRequest(String clientOrderId, MarketProviderId provider, MarketType marketType, String symbol,
                                 ExecutionOrderType orderType, OrderSide orderSide, PositionSide positionSide,
                                 BigDecimal quantity, boolean reduceOnly, Instant requestedAt) {
        if (blank(clientOrderId) || provider == null || marketType == null || blank(symbol) || orderType == null
                || orderSide == null || positionSide == null || quantity == null || quantity.signum() <= 0 || requestedAt == null) {
            throw invalid("required order field is missing or invalid");
        }
        if (orderType != ExecutionOrderType.MARKET || positionSide != PositionSide.LONG) {
            throw invalid("only MARKET and LONG are supported");
        }
        if ((orderSide == OrderSide.BUY && reduceOnly) || (orderSide == OrderSide.SELL && !reduceOnly)) {
            throw invalid("unsupported side and reduceOnly combination");
        }
        this.clientOrderId = clientOrderId;
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.orderType = orderType;
        this.orderSide = orderSide;
        this.positionSide = positionSide;
        this.quantity = quantity;
        this.reduceOnly = reduceOnly;
        this.requestedAt = requestedAt;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ExecutionOrderException invalid(String detail) {
        return new ExecutionOrderException("EXECUTION_ORDER_REQUEST_INVALID", detail);
    }

    public String getClientOrderId() { return clientOrderId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public ExecutionOrderType getOrderType() { return orderType; }
    public OrderSide getOrderSide() { return orderSide; }
    public PositionSide getPositionSide() { return positionSide; }
    public BigDecimal getQuantity() { return quantity; }
    public boolean isReduceOnly() { return reduceOnly; }
    public Instant getRequestedAt() { return requestedAt; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExecutionOrderRequest that)) return false;
        return reduceOnly == that.reduceOnly && Objects.equals(clientOrderId, that.clientOrderId)
                && provider == that.provider && marketType == that.marketType && Objects.equals(symbol, that.symbol)
                && orderType == that.orderType && orderSide == that.orderSide && positionSide == that.positionSide
                && Objects.equals(quantity, that.quantity) && Objects.equals(requestedAt, that.requestedAt);
    }

    @Override public int hashCode() { return Objects.hash(clientOrderId, provider, marketType, symbol, orderType, orderSide, positionSide, quantity, reduceOnly, requestedAt); }
}
