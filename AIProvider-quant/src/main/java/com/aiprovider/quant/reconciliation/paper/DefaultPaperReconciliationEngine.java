package com.aiprovider.quant.reconciliation.paper;

import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.account.paper.PaperAppliedFill;
import com.aiprovider.quant.account.paper.PaperPositionSnapshot;
import com.aiprovider.quant.engine.paper.PaperTradingSessionConfig;
import com.aiprovider.quant.engine.paper.PaperTradingSessionSnapshot;
import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.execution.order.ExecutionOrderSnapshot;
import com.aiprovider.quant.execution.order.ExecutionOrderStatus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultPaperReconciliationEngine implements PaperReconciliationEngine {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    @Override
    public PaperReconciliationReport reconcile(PaperReconciliationRequest request) {
        validateRequest(request);
        try {
            return reconcileValidRequest(request);
        } catch (PaperReconciliationException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new PaperReconciliationException(
                    PaperReconciliationException.PAPER_RECONCILIATION_CALCULATION_FAILED,
                    "Paper reconciliation calculation failed",
                    exception);
        }
    }

    private PaperReconciliationReport reconcileValidRequest(PaperReconciliationRequest request) {
        PaperTradingSessionSnapshot session = request.getSession();
        PaperTradingSessionConfig config = session.getConfig();
        PaperAccountSnapshot account = session.getPaperAccountSnapshot();
        List<ExecutionOrderSnapshot> orders = request.getOrderHistory();
        List<PaperReconciliationViolation> violations = new ArrayList<>();

        checkContexts(orders, config, violations);
        checkIdentities(orders, violations);
        checkTimes(orders, violations);
        checkOrderArithmetic(orders, config, violations);
        checkOrderStatuses(orders, violations);
        checkAccountFills(orders, account.getAppliedFills(), violations);
        checkCurrentOrders(orders, session, violations);
        ReplayResult replay = replayPosition(orders, violations);
        checkPosition(replay, account.getPosition(), config.getSymbol(), violations);
        checkAccountAndSessionTimes(session, account, violations);

        int orderFillCount = 0;
        Instant latestFillTime = null;
        for (ExecutionOrderSnapshot order : orders) {
            orderFillCount = Math.addExact(orderFillCount, order.getFills().size());
            for (ExecutionFill fill : order.getFills()) {
                if (latestFillTime == null || fill.getFilledAt().isAfter(latestFillTime)) {
                    latestFillTime = fill.getFilledAt();
                }
            }
        }

        return new PaperReconciliationReport(
                violations,
                orders.size(),
                orderFillCount,
                account.getAppliedFills().size(),
                replay.quantity,
                replay.openingClientOrderId,
                replay.averageEntryPrice,
                latestFillTime,
                request.getReconciledAt());
    }

    private void validateRequest(PaperReconciliationRequest request) {
        if (request == null) {
            throw invalid("Request must not be null");
        }
        if (request.getSession() == null) {
            throw invalid("Session must not be null");
        }
        if (request.getOrderHistory() == null) {
            throw invalid("OrderHistory must not be null");
        }
        if (request.getReconciledAt() == null) {
            throw invalid("ReconciledAt must not be null");
        }
        PaperTradingSessionSnapshot session = request.getSession();
        if (session.getConfig() == null
                || session.getPaperAccountSnapshot() == null
                || session.getLastUpdatedAt() == null) {
            throw invalid("Session contains a missing required field");
        }
        PaperAccountSnapshot account = session.getPaperAccountSnapshot();
        if (account.getPosition() == null
                || account.getAppliedFills() == null
                || account.getLastUpdatedAt() == null) {
            throw invalid("Account contains a missing required field");
        }
        for (ExecutionOrderSnapshot order : request.getOrderHistory()) {
            if (order == null) {
                throw invalid("OrderHistory must not contain null");
            }
            if (order.getRequest() == null
                    || order.getStatus() == null
                    || order.getFilledQuantity() == null
                    || order.getRemainingQuantity() == null
                    || order.getCumulativeFee() == null
                    || order.getFills() == null
                    || order.getLastUpdatedAt() == null) {
                throw invalid("Order contains a missing required field");
            }
            ExecutionOrderRequest orderRequest = order.getRequest();
            if (blank(orderRequest.getClientOrderId())
                    || orderRequest.getProvider() == null
                    || orderRequest.getMarketType() == null
                    || blank(orderRequest.getSymbol())
                    || orderRequest.getOrderType() == null
                    || orderRequest.getOrderSide() == null
                    || orderRequest.getPositionSide() == null
                    || orderRequest.getQuantity() == null
                    || orderRequest.getRequestedAt() == null) {
                throw invalid("Order request contains a missing required field");
            }
            for (ExecutionFill fill : order.getFills()) {
                if (fill == null
                        || blank(fill.getFillId())
                        || fill.getQuantity() == null
                        || fill.getPrice() == null
                        || fill.getFee() == null
                        || blank(fill.getFeeAsset())
                        || fill.getFilledAt() == null) {
                    throw invalid("Order fill contains a missing required field");
                }
            }
        }
        for (PaperAppliedFill fill : account.getAppliedFills()) {
            if (fill == null
                    || blank(fill.getClientOrderId())
                    || blank(fill.getFillId())
                    || fill.getQuantity() == null
                    || fill.getPrice() == null
                    || fill.getFee() == null
                    || blank(fill.getFeeAsset())
                    || fill.getFilledAt() == null) {
                throw invalid("Account AppliedFill contains a missing required field");
            }
        }
    }

    private void checkContexts(
            List<ExecutionOrderSnapshot> orders,
            PaperTradingSessionConfig config,
            List<PaperReconciliationViolation> violations) {
        for (ExecutionOrderSnapshot order : orders) {
            ExecutionOrderRequest request = order.getRequest();
            if (request.getProvider() != config.getProvider()) {
                add(violations, PaperReconciliationViolationCode.CONTEXT_PROVIDER_MISMATCH,
                        "Order provider does not match session provider", order, null,
                        value(config.getProvider()), value(request.getProvider()));
            }
            if (request.getMarketType() != config.getMarketType()) {
                add(violations, PaperReconciliationViolationCode.CONTEXT_MARKET_TYPE_MISMATCH,
                        "Order market type does not match session market type", order, null,
                        value(config.getMarketType()), value(request.getMarketType()));
            }
            if (!Objects.equals(request.getSymbol(), config.getSymbol())) {
                add(violations, PaperReconciliationViolationCode.CONTEXT_SYMBOL_MISMATCH,
                        "Order symbol does not match session symbol", order, null,
                        config.getSymbol(), request.getSymbol());
            }
            if (request.getPositionSide() != PositionSide.LONG) {
                add(violations, PaperReconciliationViolationCode.CONTEXT_POSITION_SIDE_MISMATCH,
                        "Order position side must be LONG", order, null,
                        PositionSide.LONG.name(), value(request.getPositionSide()));
            }
            boolean open = request.getOrderSide() == OrderSide.BUY && !request.isReduceOnly();
            boolean close = request.getOrderSide() == OrderSide.SELL && request.isReduceOnly();
            if (!open && !close) {
                add(violations, PaperReconciliationViolationCode.ORDER_DIRECTION_INVALID,
                        "Order side and reduceOnly combination is invalid", order, null,
                        "BUY/LONG/non-reduce-only or SELL/LONG/reduce-only",
                        request.getOrderSide() + "/"
                                + request.getPositionSide() + "/reduceOnly=" + request.isReduceOnly());
            }
        }
    }

    private void checkIdentities(
            List<ExecutionOrderSnapshot> orders,
            List<PaperReconciliationViolation> violations) {
        Set<String> clientOrderIds = new HashSet<>();
        Set<String> executionOrderIds = new HashSet<>();
        Set<FillKey> fillKeys = new HashSet<>();
        for (ExecutionOrderSnapshot order : orders) {
            if (!clientOrderIds.add(order.getRequest().getClientOrderId())) {
                add(violations, PaperReconciliationViolationCode.CLIENT_ORDER_ID_DUPLICATE,
                        "ClientOrderId is duplicated in OrderHistory", order, null,
                        "unique", order.getRequest().getClientOrderId());
            }
            if (order.getExecutionOrderId() != null
                    && !executionOrderIds.add(order.getExecutionOrderId())) {
                add(violations, PaperReconciliationViolationCode.EXECUTION_ORDER_ID_DUPLICATE,
                        "ExecutionOrderId is duplicated in OrderHistory", order, null,
                        "unique", order.getExecutionOrderId());
            }
            for (ExecutionFill fill : order.getFills()) {
                FillKey key = new FillKey(order.getRequest().getClientOrderId(), fill.getFillId());
                if (!fillKeys.add(key)) {
                    add(violations, PaperReconciliationViolationCode.FILL_APPLICATION_KEY_DUPLICATE,
                            "ClientOrderId and FillId application key is duplicated", order, fill,
                            "unique", key.toString());
                }
            }
        }
    }

    private void checkTimes(
            List<ExecutionOrderSnapshot> orders,
            List<PaperReconciliationViolation> violations) {
        for (int index = 1; index < orders.size(); index++) {
            ExecutionOrderSnapshot previous = orders.get(index - 1);
            ExecutionOrderSnapshot current = orders.get(index);
            if (current.getRequest().getRequestedAt()
                    .isBefore(previous.getRequest().getRequestedAt())) {
                add(violations, PaperReconciliationViolationCode.ORDER_HISTORY_UNSORTED,
                        "OrderHistory requestedAt moved backwards", current, null,
                        ">= " + previous.getRequest().getRequestedAt(),
                        current.getRequest().getRequestedAt().toString());
            }
        }
        for (ExecutionOrderSnapshot order : orders) {
            Instant previousFillTime = null;
            for (ExecutionFill fill : order.getFills()) {
                if (fill.getFilledAt().isBefore(order.getRequest().getRequestedAt())) {
                    add(violations, PaperReconciliationViolationCode.FILL_TIME_INVALID,
                            "Fill time precedes order requestedAt", order, fill,
                            ">= " + order.getRequest().getRequestedAt(), fill.getFilledAt().toString());
                }
                if (previousFillTime != null && fill.getFilledAt().isBefore(previousFillTime)) {
                    add(violations, PaperReconciliationViolationCode.FILL_HISTORY_UNSORTED,
                            "Order fill history moved backwards", order, fill,
                            ">= " + previousFillTime, fill.getFilledAt().toString());
                }
                previousFillTime = fill.getFilledAt();
            }
        }
    }

    private void checkOrderArithmetic(
            List<ExecutionOrderSnapshot> orders,
            PaperTradingSessionConfig config,
            List<PaperReconciliationViolation> violations) {
        String expectedFeeAsset = config.getSimulatedExecutionPolicy().getFeeAsset();
        for (ExecutionOrderSnapshot order : orders) {
            BigDecimal calculatedFilled = BigDecimal.ZERO;
            BigDecimal notional = BigDecimal.ZERO;
            BigDecimal calculatedFee = BigDecimal.ZERO;
            String fillFeeAsset = null;
            boolean mixedFillFeeAsset = false;
            for (ExecutionFill fill : order.getFills()) {
                calculatedFilled = calculatedFilled.add(fill.getQuantity());
                notional = notional.add(fill.getQuantity().multiply(fill.getPrice()));
                calculatedFee = calculatedFee.add(fill.getFee());
                if (fillFeeAsset == null) {
                    fillFeeAsset = fill.getFeeAsset();
                } else if (!fillFeeAsset.equals(fill.getFeeAsset())) {
                    mixedFillFeeAsset = true;
                }
            }
            if (!numericEquals(calculatedFilled, order.getFilledQuantity())) {
                add(violations, PaperReconciliationViolationCode.ORDER_FILLED_QUANTITY_MISMATCH,
                        "Order filledQuantity does not equal the sum of fills", order, null,
                        decimal(calculatedFilled), decimal(order.getFilledQuantity()));
            }
            BigDecimal calculatedRemaining =
                    order.getRequest().getQuantity().subtract(calculatedFilled);
            if (!numericEquals(calculatedRemaining, order.getRemainingQuantity())
                    || calculatedRemaining.signum() < 0) {
                add(violations, PaperReconciliationViolationCode.ORDER_REMAINING_QUANTITY_MISMATCH,
                        "Order remainingQuantity does not equal request quantity minus fills",
                        order, null, decimal(calculatedRemaining), decimal(order.getRemainingQuantity()));
            }
            if (calculatedFilled.signum() == 0) {
                if (order.getAveragePrice() != null) {
                    add(violations, PaperReconciliationViolationCode.ORDER_AVERAGE_PRICE_MISMATCH,
                            "Order without fills must not have averagePrice", order, null,
                            null, decimal(order.getAveragePrice()));
                }
            } else {
                BigDecimal average = notional.divide(calculatedFilled, CALCULATION_CONTEXT);
                if (!numericEquals(average, order.getAveragePrice())) {
                    add(violations, PaperReconciliationViolationCode.ORDER_AVERAGE_PRICE_MISMATCH,
                            "Order averagePrice does not equal weighted fill price", order, null,
                            decimal(average), decimal(order.getAveragePrice()));
                }
            }
            if (!numericEquals(calculatedFee, order.getCumulativeFee())) {
                add(violations, PaperReconciliationViolationCode.ORDER_CUMULATIVE_FEE_MISMATCH,
                        "Order cumulativeFee does not equal the sum of fill fees", order, null,
                        decimal(calculatedFee), decimal(order.getCumulativeFee()));
            }
            if (!order.getFills().isEmpty()
                    && (mixedFillFeeAsset
                    || !Objects.equals(fillFeeAsset, order.getFeeAsset())
                    || !Objects.equals(fillFeeAsset, expectedFeeAsset))) {
                add(violations, PaperReconciliationViolationCode.ORDER_FEE_ASSET_MISMATCH,
                        "Fill and order fee assets must be uniform and match session execution policy",
                        order, null, expectedFeeAsset,
                        mixedFillFeeAsset ? "mixed fill fee assets" : order.getFeeAsset());
            }
        }
    }

    private void checkOrderStatuses(
            List<ExecutionOrderSnapshot> orders,
            List<PaperReconciliationViolation> violations) {
        for (ExecutionOrderSnapshot order : orders) {
            BigDecimal requested = order.getRequest().getQuantity();
            BigDecimal filled = order.getFilledQuantity();
            BigDecimal remaining = order.getRemainingQuantity();
            boolean invalid;
            switch (order.getStatus()) {
                case CREATED:
                case ACCEPTED:
                case SUBMITTED:
                    invalid = !order.getFills().isEmpty()
                            || filled.signum() != 0
                            || !numericEquals(remaining, requested);
                    break;
                case PARTIALLY_FILLED:
                    invalid = filled.signum() <= 0
                            || filled.compareTo(requested) >= 0
                            || remaining.signum() <= 0;
                    break;
                case FILLED:
                    invalid = !numericEquals(filled, requested)
                            || remaining.signum() != 0
                            || order.getCompletedAt() == null;
                    break;
                case REJECTED:
                    invalid = !order.getFills().isEmpty();
                    break;
                case CANCELED:
                case FAILED:
                    invalid = !numericEquals(filled.add(remaining), requested);
                    break;
                default:
                    invalid = true;
            }
            if (invalid) {
                add(violations, PaperReconciliationViolationCode.ORDER_STATUS_QUANTITY_INVALID,
                        "Order status, fills and quantities are inconsistent", order, null,
                        order.getStatus().name() + " quantity invariants",
                        "filled=" + decimal(filled) + ", remaining=" + decimal(remaining)
                                + ", fills=" + order.getFills().size());
            }
        }
    }

    private void checkAccountFills(
            List<ExecutionOrderSnapshot> orders,
            List<PaperAppliedFill> accountFills,
            List<PaperReconciliationViolation> violations) {
        Map<FillKey, List<PaperAppliedFill>> accountByKey = new HashMap<>();
        for (PaperAppliedFill fill : accountFills) {
            accountByKey.computeIfAbsent(
                    new FillKey(fill.getClientOrderId(), fill.getFillId()),
                    ignored -> new ArrayList<>()).add(fill);
        }
        Set<FillKey> orderKeys = new HashSet<>();
        for (ExecutionOrderSnapshot order : orders) {
            for (ExecutionFill fill : order.getFills()) {
                FillKey key = new FillKey(order.getRequest().getClientOrderId(), fill.getFillId());
                orderKeys.add(key);
                List<PaperAppliedFill> candidates = accountByKey.get(key);
                if (candidates == null || candidates.isEmpty()) {
                    add(violations, PaperReconciliationViolationCode.ACCOUNT_FILL_MISSING,
                            "Order fill is missing from account AppliedFills", order, fill,
                            key.toString(), null);
                } else if (candidates.stream().noneMatch(candidate -> fillContentEquals(fill, candidate))) {
                    add(violations, PaperReconciliationViolationCode.ACCOUNT_FILL_CONTENT_MISMATCH,
                            "Account AppliedFill content differs from order fill", order, fill,
                            describe(fill), describe(candidates.get(0)));
                }
            }
        }
        Set<FillKey> seenAccountKeys = new HashSet<>();
        for (PaperAppliedFill fill : accountFills) {
            FillKey key = new FillKey(fill.getClientOrderId(), fill.getFillId());
            if (!orderKeys.contains(key) || !seenAccountKeys.add(key)) {
                violations.add(new PaperReconciliationViolation(
                        PaperReconciliationViolationCode.ACCOUNT_FILL_UNEXPECTED,
                        !orderKeys.contains(key)
                                ? "Account AppliedFill has no matching order fill"
                                : "Account AppliedFill application key is duplicated",
                        fill.getClientOrderId(), null, fill.getFillId(),
                        !orderKeys.contains(key) ? null : "one application per key",
                        key.toString()));
            }
        }
    }

    private void checkCurrentOrders(
            List<ExecutionOrderSnapshot> orders,
            PaperTradingSessionSnapshot session,
            List<PaperReconciliationViolation> violations) {
        ExecutionOrderSnapshot pending = session.getPendingOrderSnapshot();
        ExecutionOrderSnapshot last = session.getLastOrderSnapshot();
        int activeCount = 0;
        for (ExecutionOrderSnapshot order : orders) {
            if (isActive(order)) {
                activeCount++;
            }
        }

        if (pending != null) {
            ExecutionOrderSnapshot latestMatching = null;
            for (ExecutionOrderSnapshot order : orders) {
                if (order.getRequest().getClientOrderId()
                        .equals(pending.getRequest().getClientOrderId())) {
                    latestMatching = order;
                }
            }
            if (latestMatching == null) {
                add(violations, PaperReconciliationViolationCode.PENDING_ORDER_MISSING_FROM_HISTORY,
                        "Session PendingOrder is missing from OrderHistory", pending, null,
                        pending.getRequest().getClientOrderId(), null);
            } else if (!snapshotEquals(pending, latestMatching)) {
                add(violations, PaperReconciliationViolationCode.PENDING_ORDER_SNAPSHOT_MISMATCH,
                        "Session PendingOrder differs from its latest history snapshot", pending, null,
                        describe(latestMatching), describe(pending));
            }
            if (!isActive(pending)) {
                add(violations, PaperReconciliationViolationCode.PENDING_ORDER_SNAPSHOT_MISMATCH,
                        "Session PendingOrder is not active", pending, null,
                        "SUBMITTED or PARTIALLY_FILLED", pending.getStatus().name());
            }
            if (!snapshotEquals(pending, last)) {
                add(violations, PaperReconciliationViolationCode.PENDING_ORDER_SNAPSHOT_MISMATCH,
                        "Session PendingOrder must equal Session LastOrder", pending, null,
                        describe(last), describe(pending));
            }
        } else if (activeCount > 0) {
            ExecutionOrderSnapshot active = null;
            for (ExecutionOrderSnapshot order : orders) {
                if (isActive(order)) {
                    active = order;
                    break;
                }
            }
            add(violations, PaperReconciliationViolationCode.PENDING_ORDER_SNAPSHOT_MISMATCH,
                    "OrderHistory contains an active order but Session PendingOrder is null",
                    active, null, describe(active), null);
        }

        if (activeCount > 1) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.MULTIPLE_ACTIVE_ORDERS,
                    "OrderHistory contains more than one active order",
                    null, null, null, "at most 1", Integer.toString(activeCount)));
        }

        ExecutionOrderSnapshot expectedLast = orders.isEmpty() ? null : orders.get(orders.size() - 1);
        if (!snapshotEquals(last, expectedLast)) {
            add(violations, PaperReconciliationViolationCode.LAST_ORDER_MISMATCH,
                    "Session LastOrder does not equal the final OrderHistory item",
                    last != null ? last : expectedLast, null,
                    describe(expectedLast), describe(last));
        }
    }

    private ReplayResult replayPosition(
            List<ExecutionOrderSnapshot> orders,
            List<PaperReconciliationViolation> violations) {
        BigDecimal quantity = BigDecimal.ZERO;
        String openingClientOrderId = null;
        ExecutionOrderSnapshot openingOrder = null;
        for (ExecutionOrderSnapshot order : orders) {
            boolean buy = order.getRequest().getOrderSide() == OrderSide.BUY;
            if (buy && !order.getFills().isEmpty() && quantity.signum() != 0
                    && !Objects.equals(openingClientOrderId,
                    order.getRequest().getClientOrderId())) {
                add(violations, PaperReconciliationViolationCode.POSITION_PYRAMIDING_DETECTED,
                        "A new BUY order filled while a position was already open", order,
                        order.getFills().get(0), "quantity=0", decimal(quantity));
            }
            for (ExecutionFill fill : order.getFills()) {
                if (buy) {
                    if (quantity.signum() == 0) {
                        openingClientOrderId = order.getRequest().getClientOrderId();
                        openingOrder = order;
                    }
                    quantity = quantity.add(fill.getQuantity());
                } else {
                    quantity = quantity.subtract(fill.getQuantity());
                    if (quantity.signum() < 0) {
                        add(violations, PaperReconciliationViolationCode.POSITION_QUANTITY_NEGATIVE,
                                "SELL fill reduced replayed position below zero", order, fill,
                                ">= 0", decimal(quantity));
                    } else if (quantity.signum() == 0) {
                        openingClientOrderId = null;
                        openingOrder = null;
                    }
                }
            }
        }
        BigDecimal averageEntryPrice = BigDecimal.ZERO;
        if (quantity.signum() > 0 && openingOrder != null) {
            BigDecimal openingQuantity = BigDecimal.ZERO;
            BigDecimal openingNotional = BigDecimal.ZERO;
            for (ExecutionFill fill : openingOrder.getFills()) {
                openingQuantity = openingQuantity.add(fill.getQuantity());
                openingNotional = openingNotional.add(fill.getQuantity().multiply(fill.getPrice()));
            }
            if (openingQuantity.signum() > 0) {
                averageEntryPrice =
                        openingNotional.divide(openingQuantity, CALCULATION_CONTEXT);
            }
        }
        return new ReplayResult(
                quantity, openingClientOrderId, averageEntryPrice);
    }

    private void checkPosition(
            ReplayResult replay,
            PaperPositionSnapshot position,
            String sessionSymbol,
            List<PaperReconciliationViolation> violations) {
        boolean expectedOpen = replay.quantity.signum() > 0;
        if (position.isOpen() != expectedOpen
                || (expectedOpen && !Objects.equals(position.getSymbol(), sessionSymbol))) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.POSITION_OPEN_STATE_MISMATCH,
                    "Account position open state or symbol differs from replay",
                    replay.openingClientOrderId, null, null,
                    expectedOpen ? "open:" + sessionSymbol : "flat",
                    position.isOpen() ? "open:" + position.getSymbol() : "flat"));
        }
        if (!numericEquals(position.getQuantity(), replay.quantity)) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.POSITION_QUANTITY_MISMATCH,
                    "Account position quantity differs from replay",
                    replay.openingClientOrderId, null, null,
                    decimal(replay.quantity), decimal(position.getQuantity())));
        }
        if (!Objects.equals(position.getOpeningClientOrderId(), replay.openingClientOrderId)) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.POSITION_OPENING_ORDER_MISMATCH,
                    "Account opening ClientOrderId differs from replay",
                    replay.openingClientOrderId, null, null,
                    replay.openingClientOrderId, position.getOpeningClientOrderId()));
        }
        if (!numericEquals(position.getAverageEntryPrice(), replay.averageEntryPrice)) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.POSITION_AVERAGE_ENTRY_PRICE_MISMATCH,
                    "Account average entry price differs from replay",
                    replay.openingClientOrderId, null, null,
                    decimal(replay.averageEntryPrice), decimal(position.getAverageEntryPrice())));
        }
    }

    private void checkAccountAndSessionTimes(
            PaperTradingSessionSnapshot session,
            PaperAccountSnapshot account,
            List<PaperReconciliationViolation> violations) {
        Instant latestAppliedFillTime = null;
        for (PaperAppliedFill fill : account.getAppliedFills()) {
            if (latestAppliedFillTime == null || fill.getFilledAt().isAfter(latestAppliedFillTime)) {
                latestAppliedFillTime = fill.getFilledAt();
            }
        }
        if (latestAppliedFillTime != null
                && account.getLastUpdatedAt().isBefore(latestAppliedFillTime)) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.ACCOUNT_TIME_INVALID,
                    "Account lastUpdatedAt precedes its latest AppliedFill",
                    null, null, null,
                    ">= " + latestAppliedFillTime, account.getLastUpdatedAt().toString()));
        }
        if (!session.getLastUpdatedAt().equals(account.getLastUpdatedAt())) {
            violations.add(new PaperReconciliationViolation(
                    PaperReconciliationViolationCode.SESSION_TIME_INVALID,
                    "Session lastUpdatedAt does not equal account lastUpdatedAt",
                    null, null, null,
                    account.getLastUpdatedAt().toString(), session.getLastUpdatedAt().toString()));
        }
        checkOrderNotAfterSession(
                session.getPendingOrderSnapshot(), "PendingOrder", session, violations);
        checkOrderNotAfterSession(
                session.getLastOrderSnapshot(), "LastOrder", session, violations);
    }

    private void checkOrderNotAfterSession(
            ExecutionOrderSnapshot order,
            String label,
            PaperTradingSessionSnapshot session,
            List<PaperReconciliationViolation> violations) {
        if (order != null && order.getLastUpdatedAt().isAfter(session.getLastUpdatedAt())) {
            add(violations, PaperReconciliationViolationCode.SESSION_TIME_INVALID,
                    label + " lastUpdatedAt is later than Session lastUpdatedAt", order, null,
                    "<= " + session.getLastUpdatedAt(), order.getLastUpdatedAt().toString());
        }
    }

    private boolean fillContentEquals(ExecutionFill orderFill, PaperAppliedFill accountFill) {
        return numericEquals(orderFill.getQuantity(), accountFill.getQuantity())
                && numericEquals(orderFill.getPrice(), accountFill.getPrice())
                && numericEquals(orderFill.getFee(), accountFill.getFee())
                && Objects.equals(orderFill.getFeeAsset(), accountFill.getFeeAsset())
                && Objects.equals(orderFill.getFilledAt(), accountFill.getFilledAt());
    }

    private boolean snapshotEquals(
            ExecutionOrderSnapshot left,
            ExecutionOrderSnapshot right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (!requestEquals(left.getRequest(), right.getRequest())
                || left.getStatus() != right.getStatus()
                || !Objects.equals(left.getExecutionOrderId(), right.getExecutionOrderId())
                || !numericEquals(left.getFilledQuantity(), right.getFilledQuantity())
                || !numericEquals(left.getRemainingQuantity(), right.getRemainingQuantity())
                || !numericEquals(left.getAveragePrice(), right.getAveragePrice())
                || !numericEquals(left.getCumulativeFee(), right.getCumulativeFee())
                || !Objects.equals(left.getFeeAsset(), right.getFeeAsset())
                || left.getFills().size() != right.getFills().size()
                || !Objects.equals(left.getAcceptedAt(), right.getAcceptedAt())
                || !Objects.equals(left.getSubmittedAt(), right.getSubmittedAt())
                || !Objects.equals(left.getLastUpdatedAt(), right.getLastUpdatedAt())
                || !Objects.equals(left.getCompletedAt(), right.getCompletedAt())
                || !Objects.equals(left.getTerminalErrorCode(), right.getTerminalErrorCode())
                || !Objects.equals(left.getTerminalErrorMessage(), right.getTerminalErrorMessage())) {
            return false;
        }
        for (int index = 0; index < left.getFills().size(); index++) {
            if (!fillEquals(left.getFills().get(index), right.getFills().get(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean requestEquals(
            ExecutionOrderRequest left,
            ExecutionOrderRequest right) {
        return Objects.equals(left.getClientOrderId(), right.getClientOrderId())
                && left.getProvider() == right.getProvider()
                && left.getMarketType() == right.getMarketType()
                && Objects.equals(left.getSymbol(), right.getSymbol())
                && left.getOrderType() == right.getOrderType()
                && left.getOrderSide() == right.getOrderSide()
                && left.getPositionSide() == right.getPositionSide()
                && numericEquals(left.getQuantity(), right.getQuantity())
                && left.isReduceOnly() == right.isReduceOnly()
                && Objects.equals(left.getRequestedAt(), right.getRequestedAt());
    }

    private boolean fillEquals(ExecutionFill left, ExecutionFill right) {
        return Objects.equals(left.getFillId(), right.getFillId())
                && numericEquals(left.getQuantity(), right.getQuantity())
                && numericEquals(left.getPrice(), right.getPrice())
                && numericEquals(left.getFee(), right.getFee())
                && Objects.equals(left.getFeeAsset(), right.getFeeAsset())
                && Objects.equals(left.getFilledAt(), right.getFilledAt());
    }

    private boolean isActive(ExecutionOrderSnapshot order) {
        return order != null && (order.getStatus() == ExecutionOrderStatus.SUBMITTED
                || order.getStatus() == ExecutionOrderStatus.PARTIALLY_FILLED);
    }

    private static boolean numericEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    private static String describe(ExecutionOrderSnapshot order) {
        if (order == null) return null;
        return order.getRequest().getClientOrderId() + ":" + order.getStatus()
                + ":" + decimal(order.getFilledQuantity()) + ":" + decimal(order.getRemainingQuantity())
                + ":" + order.getLastUpdatedAt();
    }

    private static String describe(ExecutionFill fill) {
        return decimal(fill.getQuantity()) + ":" + decimal(fill.getPrice()) + ":"
                + decimal(fill.getFee()) + ":" + fill.getFeeAsset() + ":" + fill.getFilledAt();
    }

    private static String describe(PaperAppliedFill fill) {
        return decimal(fill.getQuantity()) + ":" + decimal(fill.getPrice()) + ":"
                + decimal(fill.getFee()) + ":" + fill.getFeeAsset() + ":" + fill.getFilledAt();
    }

    private void add(
            List<PaperReconciliationViolation> violations,
            PaperReconciliationViolationCode code,
            String message,
            ExecutionOrderSnapshot order,
            ExecutionFill fill,
            String expected,
            String actual) {
        violations.add(new PaperReconciliationViolation(
                code,
                message,
                order == null ? null : order.getRequest().getClientOrderId(),
                order == null ? null : order.getExecutionOrderId(),
                fill == null ? null : fill.getFillId(),
                expected,
                actual));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private PaperReconciliationException invalid(String message) {
        return new PaperReconciliationException(
                PaperReconciliationException.PAPER_RECONCILIATION_REQUEST_INVALID,
                message);
    }

    private static final class FillKey {
        private final String clientOrderId;
        private final String fillId;

        private FillKey(String clientOrderId, String fillId) {
            this.clientOrderId = clientOrderId;
            this.fillId = fillId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FillKey that)) return false;
            return Objects.equals(clientOrderId, that.clientOrderId)
                    && Objects.equals(fillId, that.fillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientOrderId, fillId);
        }

        @Override
        public String toString() {
            return clientOrderId + ":" + fillId;
        }
    }

    private static final class ReplayResult {
        private final BigDecimal quantity;
        private final String openingClientOrderId;
        private final BigDecimal averageEntryPrice;

        private ReplayResult(
                BigDecimal quantity,
                String openingClientOrderId,
                BigDecimal averageEntryPrice) {
            this.quantity = quantity;
            this.openingClientOrderId = openingClientOrderId;
            this.averageEntryPrice = averageEntryPrice;
        }
    }
}
