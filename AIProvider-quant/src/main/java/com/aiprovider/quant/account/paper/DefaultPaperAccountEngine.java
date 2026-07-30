package com.aiprovider.quant.account.paper;

import com.aiprovider.quant.execution.OrderSide;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.execution.order.ExecutionFill;
import com.aiprovider.quant.execution.order.ExecutionOrderRequest;
import com.aiprovider.quant.market.model.MarketProviderId;
import com.aiprovider.quant.market.model.MarketType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DefaultPaperAccountEngine implements PaperAccountEngine {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    @Override
    public PaperAccountSnapshot restore(PaperAccountRestoreRequest request) {
        if (request == null) throw restoreFailure("request is null");
        requireRestoreText("accountId", request.getAccountId());
        if (request.getProvider() == null) throw restoreFailure("provider is required");
        if (request.getMarketType() != MarketType.USDM_PERPETUAL) {
            throw restoreFailure("marketType must be USDM_PERPETUAL");
        }
        requireRestoreText("quoteAsset", request.getQuoteAsset());
        requireAmount("initialCapital", request.getInitialCapital());
        requireAmount("realizedPnl", request.getRealizedPnl());
        requireAmount("unrealizedPnl", request.getUnrealizedPnl());
        requireAmount("totalEquity", request.getTotalEquity());
        requireAmount("availableCapital", request.getAvailableCapital());
        if (request.getInitialCapital().signum() <= 0) {
            throw restoreFailure("initialCapital must be positive");
        }
        if (request.getConsecutiveLosses() < 0) {
            throw restoreFailure("consecutiveLosses must not be negative");
        }
        if (request.getTradingUtcDate() == null) throw restoreFailure("tradingUtcDate is required");
        requireAmount("dayStartEquity", request.getDayStartEquity());
        requireAmount("dailyRealizedPnl", request.getDailyRealizedPnl());
        if (request.getDayStartEquity().signum() < 0) {
            throw restoreFailure("dayStartEquity must not be negative");
        }
        if (request.getLastUpdatedAt() == null) throw restoreFailure("lastUpdatedAt is required");
        LocalDate lastUpdatedUtcDate = request.getLastUpdatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (request.getTradingUtcDate().isAfter(lastUpdatedUtcDate)) {
            throw restoreFailure("tradingUtcDate must not be later than lastUpdatedAt UTC date");
        }

        requireAmount("positionQuantity", request.getPositionQuantity());
        requireAmount("averageEntryPrice", request.getAverageEntryPrice());
        requireAmount("markPrice", request.getMarkPrice());
        requireAmount("positionNotional", request.getPositionNotional());
        requireAmount("positionUnrealizedPnl", request.getPositionUnrealizedPnl());
        requireAmount("openTradeNetPnl", request.getOpenTradeNetPnl());
        PaperPositionSnapshot position;
        if (!request.isPositionOpen()) {
            requireFlatPosition(request);
            position = PaperPositionSnapshot.flat();
        } else {
            requireOpenPosition(request);
            position = PaperPositionSnapshot.open(
                    request.getPositionSymbol(), request.getPositionQuantity(),
                    request.getAverageEntryPrice(), request.getMarkPrice(),
                    request.getPositionNotional(), request.getPositionUnrealizedPnl(),
                    request.getOpeningClientOrderId(), request.getOpenTradeNetPnl());
        }

        if (request.getUnrealizedPnl().compareTo(request.getPositionUnrealizedPnl()) != 0) {
            throw restoreFailure("unrealizedPnl must equal positionUnrealizedPnl");
        }
        BigDecimal expectedTotal = request.getInitialCapital()
                .add(request.getRealizedPnl()).add(request.getUnrealizedPnl());
        if (request.getTotalEquity().compareTo(expectedTotal) != 0) {
            throw restoreFailure("totalEquity does not equal initialCapital + realizedPnl + unrealizedPnl");
        }
        BigDecimal expectedAvailable = expectedTotal.subtract(request.getPositionNotional())
                .max(BigDecimal.ZERO);
        if (request.getAvailableCapital().compareTo(expectedAvailable) != 0) {
            throw restoreFailure("availableCapital does not equal max(totalEquity - positionNotional, 0)");
        }

        List<PaperAppliedFill> appliedFills = validateAppliedFills(request);
        return new PaperAccountSnapshot(
                request.getAccountId(), request.getProvider(), request.getMarketType(),
                request.getQuoteAsset(), request.getInitialCapital(), request.getRealizedPnl(),
                request.getUnrealizedPnl(), request.getTotalEquity(), request.getAvailableCapital(),
                position, new PaperTradingDayState(request.getTradingUtcDate(),
                request.getDayStartEquity(), request.getDailyRealizedPnl()),
                request.getConsecutiveLosses(), appliedFills, request.getLastUpdatedAt());
    }

    private List<PaperAppliedFill> validateAppliedFills(PaperAccountRestoreRequest request) {
        List<PaperAppliedFill> source = request.getAppliedFills();
        if (source == null || source.isEmpty()) throw restoreFailure("appliedFills must not be empty");
        Set<List<String>> keys = new HashSet<>();
        List<PaperAppliedFill> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            PaperAppliedFill fill = source.get(index);
            if (fill == null) throw restoreFailure("appliedFills[" + index + "] is null");
            requireRestoreText("appliedFills[" + index + "].clientOrderId", fill.getClientOrderId());
            requireRestoreText("appliedFills[" + index + "].fillId", fill.getFillId());
            requireAmount("appliedFills[" + index + "].quantity", fill.getQuantity());
            requireAmount("appliedFills[" + index + "].price", fill.getPrice());
            requireAmount("appliedFills[" + index + "].fee", fill.getFee());
            if (fill.getQuantity().signum() <= 0) throw restoreFailure("appliedFills[" + index + "].quantity must be positive");
            if (fill.getPrice().signum() <= 0) throw restoreFailure("appliedFills[" + index + "].price must be positive");
            if (fill.getFee().signum() < 0) throw restoreFailure("appliedFills[" + index + "].fee must not be negative");
            if (!request.getQuoteAsset().equals(fill.getFeeAsset())) {
                throw restoreFailure("appliedFills[" + index + "].feeAsset must equal quoteAsset");
            }
            if (fill.getFilledAt() == null) throw restoreFailure("appliedFills[" + index + "].filledAt is required");
            if (fill.getFilledAt().isAfter(request.getLastUpdatedAt())) {
                throw restoreFailure("appliedFills[" + index + "].filledAt must not be later than lastUpdatedAt");
            }
            if (!keys.add(List.of(fill.getClientOrderId(), fill.getFillId()))) {
                throw restoreFailure("appliedFills contains duplicate (clientOrderId, fillId)");
            }
            result.add(PaperAppliedFill.restore(fill.getClientOrderId(), fill.getFillId(),
                    fill.getQuantity(), fill.getPrice(), fill.getFee(), fill.getFeeAsset(), fill.getFilledAt()));
        }
        return List.copyOf(result);
    }

    private void requireFlatPosition(PaperAccountRestoreRequest request) {
        if (request.getPositionSymbol() != null) throw restoreFailure("flat positionSymbol must be null");
        if (request.getOpeningClientOrderId() != null) throw restoreFailure("flat openingClientOrderId must be null");
        requireZero("flat positionQuantity", request.getPositionQuantity());
        requireZero("flat averageEntryPrice", request.getAverageEntryPrice());
        requireZero("flat markPrice", request.getMarkPrice());
        requireZero("flat positionNotional", request.getPositionNotional());
        requireZero("flat positionUnrealizedPnl", request.getPositionUnrealizedPnl());
        requireZero("flat openTradeNetPnl", request.getOpenTradeNetPnl());
    }

    private void requireOpenPosition(PaperAccountRestoreRequest request) {
        requireRestoreText("positionSymbol", request.getPositionSymbol());
        requireRestoreText("openingClientOrderId", request.getOpeningClientOrderId());
        if (request.getPositionQuantity().signum() <= 0) throw restoreFailure("positionQuantity must be positive");
        if (request.getAverageEntryPrice().signum() <= 0) throw restoreFailure("averageEntryPrice must be positive");
        if (request.getMarkPrice().signum() <= 0) throw restoreFailure("markPrice must be positive");
        if (request.getPositionNotional().signum() < 0) throw restoreFailure("positionNotional must not be negative");
        BigDecimal expectedNotional = request.getPositionQuantity().multiply(request.getMarkPrice());
        if (request.getPositionNotional().compareTo(expectedNotional) != 0) {
            throw restoreFailure("positionNotional does not equal positionQuantity * markPrice");
        }
        BigDecimal expectedUnrealized = request.getPositionQuantity()
                .multiply(request.getMarkPrice().subtract(request.getAverageEntryPrice()));
        if (request.getPositionUnrealizedPnl().compareTo(expectedUnrealized) != 0) {
            throw restoreFailure("positionUnrealizedPnl does not equal quantity * (markPrice - averageEntryPrice)");
        }
    }

    private void requireAmount(String field, BigDecimal value) {
        if (value == null) throw restoreFailure(field + " is required");
    }

    private void requireZero(String field, BigDecimal value) {
        requireAmount(field, value);
        if (value.signum() != 0) throw restoreFailure(field + " must be zero for a flat position");
    }

    private void requireRestoreText(String field, String value) {
        if (value == null || value.isBlank()) throw restoreFailure(field + " must not be blank");
    }

    private PaperAccountException restoreFailure(String message) {
        return new PaperAccountException(PaperAccountException.PAPER_ACCOUNT_RESTORE_INVALID, message);
    }

    @Override
    public PaperAccountSnapshot initialize(
            String accountId,
            MarketProviderId provider,
            MarketType marketType,
            String quoteAsset,
            BigDecimal initialCapital,
            LocalDate initialUtcDate,
            Instant initializedAt) {
        if (blank(accountId)
                || provider == null
                || marketType == null
                || blank(quoteAsset)
                || initialCapital == null
                || initialCapital.signum() <= 0
                || initialUtcDate == null
                || initializedAt == null) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "Paper account initialization fields are incomplete or invalid");
        }
        requireSupportedMarket(marketType);
        return new PaperAccountSnapshot(
                accountId,
                provider,
                marketType,
                quoteAsset,
                initialCapital,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                initialCapital,
                initialCapital,
                PaperPositionSnapshot.flat(),
                new PaperTradingDayState(initialUtcDate, initialCapital, BigDecimal.ZERO),
                0,
                List.of(),
                initializedAt);
    }

    @Override
    public PaperAccountUpdateResult applyFill(
            PaperAccountSnapshot account,
            ExecutionOrderRequest orderRequest,
            ExecutionFill fill) {
        requireAccount(account);
        if (orderRequest == null || fill == null) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "Order request and fill must not be null");
        }
        requireSupportedMarket(account.getMarketType());
        requireOrderContext(account, orderRequest);

        PaperAppliedFill candidate = PaperAppliedFill.from(orderRequest.getClientOrderId(), fill);
        PaperAppliedFill existing = findAppliedFill(
                account.getAppliedFills(), orderRequest.getClientOrderId(), fill.getFillId());
        if (existing != null) {
            if (existing.equals(candidate)) {
                return new PaperAccountUpdateResult(account, false);
            }
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_DUPLICATE_FILL_CONFLICT,
                    "Fill key already exists with different fill content");
        }

        requireFeeAsset(account, fill);
        requireFillTime(account, orderRequest, fill);
        try {
            PaperAccountSnapshot updated;
            if (orderRequest.getOrderSide() == OrderSide.BUY) {
                requireBuyRequest(orderRequest);
                updated = applyBuy(account, orderRequest, fill, candidate);
            } else if (orderRequest.getOrderSide() == OrderSide.SELL) {
                requireSellRequest(orderRequest);
                updated = applySell(account, orderRequest, fill, candidate);
            } else {
                throw error(
                        PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                        "Only BUY and SELL are supported");
            }
            return new PaperAccountUpdateResult(updated, true);
        } catch (PaperAccountException exception) {
            throw exception;
        } catch (ArithmeticException exception) {
            throw new PaperAccountException(
                    PaperAccountException.PAPER_ACCOUNT_CALCULATION_FAILED,
                    "Paper account fill calculation failed",
                    exception);
        }
    }

    @Override
    public PaperAccountSnapshot markToMarket(
            PaperAccountSnapshot account, String symbol, BigDecimal markPrice, Instant markedAt) {
        requireAccount(account);
        if (blank(symbol) || markPrice == null || markPrice.signum() <= 0 || markedAt == null) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "Mark-to-market fields are incomplete or invalid");
        }
        requireTimeNotBefore(markedAt, account.getLastUpdatedAt());
        PaperPositionSnapshot position = account.getPosition();
        if (position.isOpen() && !position.getSymbol().equals(symbol)) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_CONTEXT_MISMATCH,
                    "Mark symbol does not match the open position");
        }
        try {
            PaperPositionSnapshot markedPosition = position.isOpen()
                    ? valueOpenPosition(
                            position.getSymbol(),
                            position.getQuantity(),
                            position.getAverageEntryPrice(),
                            markPrice,
                            position.getOpeningClientOrderId(),
                            position.getOpenTradeNetPnl())
                    : PaperPositionSnapshot.flat();
            return revalue(
                    account,
                    account.getRealizedPnl(),
                    markedPosition,
                    account.getTradingDayState(),
                    account.getConsecutiveLosses(),
                    account.getAppliedFills(),
                    markedAt);
        } catch (ArithmeticException exception) {
            throw new PaperAccountException(
                    PaperAccountException.PAPER_ACCOUNT_CALCULATION_FAILED,
                    "Paper account mark-to-market calculation failed",
                    exception);
        }
    }

    @Override
    public PaperAccountSnapshot rollUtcTradingDay(
            PaperAccountSnapshot account, LocalDate nextUtcDate, Instant rolledAt) {
        requireAccount(account);
        if (nextUtcDate == null || rolledAt == null) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "UTC trading day and rolledAt must not be null");
        }
        if (!nextUtcDate.isAfter(account.getTradingDayState().getUtcDate())) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "UTC trading day must move to a later date");
        }
        requireTimeNotBefore(rolledAt, account.getLastUpdatedAt());
        return revalue(
                account,
                account.getRealizedPnl(),
                account.getPosition(),
                new PaperTradingDayState(nextUtcDate, account.getTotalEquity(), BigDecimal.ZERO),
                account.getConsecutiveLosses(),
                account.getAppliedFills(),
                rolledAt);
    }

    private PaperAccountSnapshot applyBuy(
            PaperAccountSnapshot account,
            ExecutionOrderRequest orderRequest,
            ExecutionFill fill,
            PaperAppliedFill appliedFill) {
        PaperPositionSnapshot current = account.getPosition();
        if (current.isOpen()) {
            if (!current.getSymbol().equals(orderRequest.getSymbol())) {
                throw error(
                        PaperAccountException.PAPER_ACCOUNT_CONTEXT_MISMATCH,
                        "Order symbol does not match the open position");
            }
            if (!current.getOpeningClientOrderId().equals(orderRequest.getClientOrderId())) {
                throw error(
                        PaperAccountException.PAPER_ACCOUNT_POSITION_ALREADY_OPEN,
                        "A different opening order cannot add to the open position");
            }
        }

        BigDecimal fillNotional = fill.getQuantity().multiply(fill.getPrice());
        BigDecimal requiredCapital = fillNotional.add(fill.getFee());
        if (requiredCapital.compareTo(account.getAvailableCapital()) > 0) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_CAPITAL_INSUFFICIENT,
                    "Fill notional and fee exceed current available capital");
        }

        BigDecimal previousQuantity = current.getQuantity();
        BigDecimal nextQuantity = previousQuantity.add(fill.getQuantity());
        BigDecimal weightedCost = current.isOpen()
                ? previousQuantity.multiply(current.getAverageEntryPrice()).add(fillNotional)
                : fillNotional;
        BigDecimal averageEntryPrice = weightedCost.divide(nextQuantity, CALCULATION_CONTEXT);
        BigDecimal realizedPnl = account.getRealizedPnl().subtract(fill.getFee());
        BigDecimal openTradeNetPnl = current.getOpenTradeNetPnl().subtract(fill.getFee());
        PaperPositionSnapshot position = valueOpenPosition(
                orderRequest.getSymbol(),
                nextQuantity,
                averageEntryPrice,
                fill.getPrice(),
                current.isOpen()
                        ? current.getOpeningClientOrderId()
                        : orderRequest.getClientOrderId(),
                openTradeNetPnl);
        PaperTradingDayState tradingDayState = new PaperTradingDayState(
                account.getTradingDayState().getUtcDate(),
                account.getTradingDayState().getDayStartEquity(),
                account.getTradingDayState().getDailyRealizedPnl().subtract(fill.getFee()));
        return revalue(
                account,
                realizedPnl,
                position,
                tradingDayState,
                account.getConsecutiveLosses(),
                append(account.getAppliedFills(), appliedFill),
                fill.getFilledAt());
    }

    private PaperAccountSnapshot applySell(
            PaperAccountSnapshot account,
            ExecutionOrderRequest orderRequest,
            ExecutionFill fill,
            PaperAppliedFill appliedFill) {
        PaperPositionSnapshot current = account.getPosition();
        if (current.isFlat()) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_POSITION_NOT_OPEN,
                    "A LONG position must be open before a SELL fill");
        }
        if (!current.getSymbol().equals(orderRequest.getSymbol())) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_CONTEXT_MISMATCH,
                    "Order symbol does not match the open position");
        }
        if (fill.getQuantity().compareTo(current.getQuantity()) > 0) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_EXIT_QUANTITY_EXCEEDED,
                    "SELL fill quantity exceeds the open position quantity");
        }

        BigDecimal pricePnl = fill.getQuantity()
                .multiply(fill.getPrice().subtract(current.getAverageEntryPrice()));
        BigDecimal netRealizedChange = pricePnl.subtract(fill.getFee());
        BigDecimal realizedPnl = account.getRealizedPnl().add(netRealizedChange);
        BigDecimal finalOpenTradeNetPnl = current.getOpenTradeNetPnl().add(netRealizedChange);
        BigDecimal remainingQuantity = current.getQuantity().subtract(fill.getQuantity());
        boolean fullyClosed = remainingQuantity.signum() == 0;
        int consecutiveLosses = account.getConsecutiveLosses();
        PaperPositionSnapshot position;
        if (fullyClosed) {
            if (finalOpenTradeNetPnl.signum() < 0) {
                consecutiveLosses++;
            } else if (finalOpenTradeNetPnl.signum() > 0) {
                consecutiveLosses = 0;
            }
            position = PaperPositionSnapshot.flat();
        } else {
            position = valueOpenPosition(
                    current.getSymbol(),
                    remainingQuantity,
                    current.getAverageEntryPrice(),
                    fill.getPrice(),
                    current.getOpeningClientOrderId(),
                    finalOpenTradeNetPnl);
        }
        PaperTradingDayState tradingDayState = new PaperTradingDayState(
                account.getTradingDayState().getUtcDate(),
                account.getTradingDayState().getDayStartEquity(),
                account.getTradingDayState().getDailyRealizedPnl().add(netRealizedChange));
        return revalue(
                account,
                realizedPnl,
                position,
                tradingDayState,
                consecutiveLosses,
                append(account.getAppliedFills(), appliedFill),
                fill.getFilledAt());
    }

    private PaperPositionSnapshot valueOpenPosition(
            String symbol,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            BigDecimal markPrice,
            String openingClientOrderId,
            BigDecimal openTradeNetPnl) {
        BigDecimal positionNotional = quantity.multiply(markPrice);
        BigDecimal unrealizedPnl = quantity.multiply(markPrice.subtract(averageEntryPrice));
        return PaperPositionSnapshot.open(
                symbol,
                quantity,
                averageEntryPrice,
                markPrice,
                positionNotional,
                unrealizedPnl,
                openingClientOrderId,
                openTradeNetPnl);
    }

    private PaperAccountSnapshot revalue(
            PaperAccountSnapshot old,
            BigDecimal realizedPnl,
            PaperPositionSnapshot position,
            PaperTradingDayState tradingDayState,
            int consecutiveLosses,
            List<PaperAppliedFill> appliedFills,
            Instant lastUpdatedAt) {
        BigDecimal unrealizedPnl = position.getUnrealizedPnl();
        BigDecimal totalEquity = old.getInitialCapital().add(realizedPnl).add(unrealizedPnl);
        BigDecimal availableCapital = totalEquity.subtract(position.getPositionNotional()).max(BigDecimal.ZERO);
        return new PaperAccountSnapshot(
                old.getAccountId(),
                old.getProvider(),
                old.getMarketType(),
                old.getQuoteAsset(),
                old.getInitialCapital(),
                realizedPnl,
                unrealizedPnl,
                totalEquity,
                availableCapital,
                position,
                tradingDayState,
                consecutiveLosses,
                appliedFills,
                lastUpdatedAt);
    }

    private void requireAccount(PaperAccountSnapshot account) {
        if (account == null) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "Paper account must not be null");
        }
    }

    private void requireSupportedMarket(MarketType marketType) {
        if (marketType != MarketType.USDM_PERPETUAL) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_MARKET_NOT_SUPPORTED,
                    "Only USDM_PERPETUAL is supported");
        }
    }

    private void requireOrderContext(
            PaperAccountSnapshot account, ExecutionOrderRequest orderRequest) {
        if (orderRequest.getProvider() != account.getProvider()
                || orderRequest.getMarketType() != account.getMarketType()) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_CONTEXT_MISMATCH,
                    "Order provider or market type does not match the account");
        }
    }

    private void requireFeeAsset(PaperAccountSnapshot account, ExecutionFill fill) {
        if (!account.getQuoteAsset().equals(fill.getFeeAsset())) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_FEE_ASSET_MISMATCH,
                    "Fill fee asset does not match the account quote asset");
        }
    }

    private void requireFillTime(
            PaperAccountSnapshot account,
            ExecutionOrderRequest orderRequest,
            ExecutionFill fill) {
        if (fill.getFilledAt().isBefore(orderRequest.getRequestedAt())
                || fill.getFilledAt().isBefore(account.getLastUpdatedAt())) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_TIME_INVALID,
                    "Fill time must not precede the order or account update time");
        }
    }

    private void requireTimeNotBefore(Instant eventAt, Instant lastUpdatedAt) {
        if (eventAt.isBefore(lastUpdatedAt)) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_TIME_INVALID,
                    "Event time must not precede the account update time");
        }
    }

    private void requireBuyRequest(ExecutionOrderRequest orderRequest) {
        if (orderRequest.getPositionSide() != PositionSide.LONG || orderRequest.isReduceOnly()) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "BUY fills require LONG and reduceOnly=false");
        }
    }

    private void requireSellRequest(ExecutionOrderRequest orderRequest) {
        if (orderRequest.getPositionSide() != PositionSide.LONG || !orderRequest.isReduceOnly()) {
            throw error(
                    PaperAccountException.PAPER_ACCOUNT_REQUEST_INVALID,
                    "SELL fills require LONG and reduceOnly=true");
        }
    }

    private PaperAppliedFill findAppliedFill(
            List<PaperAppliedFill> fills, String clientOrderId, String fillId) {
        for (PaperAppliedFill fill : fills) {
            if (fill.hasKey(clientOrderId, fillId)) {
                return fill;
            }
        }
        return null;
    }

    private List<PaperAppliedFill> append(
            List<PaperAppliedFill> existing, PaperAppliedFill appended) {
        List<PaperAppliedFill> result = new ArrayList<>(existing);
        result.add(appended);
        return List.copyOf(result);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private PaperAccountException error(String code, String message) {
        return new PaperAccountException(code, message);
    }
}
