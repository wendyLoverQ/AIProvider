package com.aiprovider.quant.audit.paper;

import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable canonical latest-snapshot ledger for one paper-trading session. */
public final class PaperOrderAuditLedger {
    private final String sessionId;
    private final MarketProviderId provider;
    private final MarketType marketType;
    private final String symbol;
    private final List<ExecutionOrderSnapshot> orderHistory;
    private final long version;
    private final Instant initializedAt;
    private final Instant lastUpdatedAt;

    public PaperOrderAuditLedger(
            String sessionId,
            MarketProviderId provider,
            MarketType marketType,
            String symbol,
            List<ExecutionOrderSnapshot> orderHistory,
            long version,
            Instant initializedAt,
            Instant lastUpdatedAt) {
        if (blank(sessionId) || provider == null || marketType == null || blank(symbol)
                || orderHistory == null || version < 0 || initializedAt == null
                || lastUpdatedAt == null) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                    "ledger fields are missing or invalid");
        }
        if (lastUpdatedAt.isBefore(initializedAt)) {
            throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_TIME_INVALID,
                    "lastUpdatedAt must not precede initializedAt");
        }
        List<ExecutionOrderSnapshot> history = new ArrayList<>(orderHistory.size());
        Set<String> clientOrderIds = new HashSet<>();
        Set<String> executionOrderIds = new HashSet<>();
        for (ExecutionOrderSnapshot order : orderHistory) {
            if (order == null || order.getRequest() == null) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_REQUEST_INVALID,
                        "orderHistory must not contain null or an order without a request");
            }
            ExecutionOrderRequest request = order.getRequest();
            if (request.getProvider() != provider || request.getMarketType() != marketType
                    || !symbol.equals(request.getSymbol())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_CONTEXT_MISMATCH,
                        "order context does not match ledger");
            }
            if (!clientOrderIds.add(request.getClientOrderId())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID,
                        "duplicate ClientOrderId=" + request.getClientOrderId());
            }
            if (order.getExecutionOrderId() != null
                    && !executionOrderIds.add(order.getExecutionOrderId())) {
                throw error(PaperOrderAuditException.PAPER_ORDER_AUDIT_DUPLICATE_ID,
                        "duplicate ExecutionOrderId=" + order.getExecutionOrderId());
            }
            history.add(order);
        }
        this.sessionId = sessionId;
        this.provider = provider;
        this.marketType = marketType;
        this.symbol = symbol;
        this.orderHistory = Collections.unmodifiableList(history);
        this.version = version;
        this.initializedAt = copy(initializedAt);
        this.lastUpdatedAt = copy(lastUpdatedAt);
    }

    public String getSessionId() { return sessionId; }
    public MarketProviderId getProvider() { return provider; }
    public MarketType getMarketType() { return marketType; }
    public String getSymbol() { return symbol; }
    public List<ExecutionOrderSnapshot> getOrderHistory() { return orderHistory; }
    public long getVersion() { return version; }
    public Instant getInitializedAt() { return copy(initializedAt); }
    public Instant getLastUpdatedAt() { return copy(lastUpdatedAt); }

    private static Instant copy(Instant value) {
        return Instant.ofEpochSecond(value.getEpochSecond(), value.getNano());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PaperOrderAuditException error(String code, String message) {
        return new PaperOrderAuditException(code, message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaperOrderAuditLedger that)) return false;
        return version == that.version
                && sessionId.equals(that.sessionId)
                && provider == that.provider
                && marketType == that.marketType
                && symbol.equals(that.symbol)
                && orderHistory.equals(that.orderHistory)
                && initializedAt.equals(that.initializedAt)
                && lastUpdatedAt.equals(that.lastUpdatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, provider, marketType, symbol, orderHistory,
                version, initializedAt, lastUpdatedAt);
    }
}
