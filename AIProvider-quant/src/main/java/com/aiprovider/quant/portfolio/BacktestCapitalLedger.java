package com.aiprovider.quant.portfolio;

import com.aiprovider.quant.backtest.BacktestException;
import com.aiprovider.quant.backtest.BacktestTrade;
import com.aiprovider.quant.execution.PositionSide;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stateless deterministic reconstruction of a long-only research account.
 *
 * <p>Entry fees are realized at entry. Exit price profit and exit fees are realized at exit.
 */
public final class BacktestCapitalLedger {
    private static final int RATIO_SCALE = 12;

    public List<BacktestPortfolioSnapshot> rebuild(
            BigDecimal initialCapital,
            BigDecimal feeRate,
            List<BacktestTrade> trades,
            List<HistoricalCandle> candles) {
        requirePositive(initialCapital, "initialCapital");
        requireNonNegative(feeRate, "feeRate");
        Objects.requireNonNull(trades, "trades");
        Objects.requireNonNull(candles, "candles");

        List<BacktestTrade> orderedTrades =
                trades.stream()
                        .sorted(
                                Comparator.comparingInt(BacktestTrade::getEntryIndex)
                                        .thenComparingInt(BacktestTrade::getTradeNo))
                        .toList();
        validateTrades(orderedTrades, candles.size());

        List<BacktestPortfolioSnapshot> snapshots = new ArrayList<>(candles.size());
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BacktestTrade activeTrade = null;
        int nextTradeIndex = 0;

        for (int barIndex = 0; barIndex < candles.size(); barIndex++) {
            if (activeTrade != null && activeTrade.getExitIndex() == barIndex) {
                realizedPnl = settleExit(realizedPnl, activeTrade, feeRate);
                activeTrade = null;
            }

            while (nextTradeIndex < orderedTrades.size()
                    && orderedTrades.get(nextTradeIndex).getEntryIndex() == barIndex) {
                BacktestTrade trade = orderedTrades.get(nextTradeIndex++);
                if (activeTrade != null) {
                    throw invalidLedger("overlapping long-only positions at barIndex=" + barIndex);
                }

                BigDecimal currentEquity = initialCapital.add(realizedPnl);
                BigDecimal entryNotional = trade.getEntryPrice().multiply(trade.getAmount());
                BigDecimal entryFee = entryNotional.multiply(feeRate);
                if (entryNotional.add(entryFee).compareTo(currentEquity) > 0) {
                    throw new BacktestException(
                            "BACKTEST_CAPITAL_INSUFFICIENT",
                            "currentEquity="
                                    + currentEquity.toPlainString()
                                    + " entryPrice="
                                    + trade.getEntryPrice().toPlainString()
                                    + " orderQuantity="
                                    + trade.getAmount().toPlainString()
                                    + " entryNotional="
                                    + entryNotional.toPlainString()
                                    + " entryFee="
                                    + entryFee.toPlainString());
                }
                realizedPnl = realizedPnl.subtract(entryFee);
                activeTrade = trade;

                if (trade.getExitIndex() == barIndex) {
                    realizedPnl = settleExit(realizedPnl, trade, feeRate);
                    activeTrade = null;
                }
            }

            BigDecimal markPrice = candles.get(barIndex).getClosePrice();
            requirePositive(markPrice, "markPrice");
            BacktestPositionSnapshot position =
                    activeTrade == null
                            ? BacktestPositionSnapshot.flat()
                            : openPosition(activeTrade, markPrice);
            BigDecimal unrealizedPnl = position.unrealizedPnl();
            BigDecimal totalEquity = initialCapital.add(realizedPnl).add(unrealizedPnl);
            if (totalEquity.signum() <= 0) {
                throw invalidLedger(
                        "totalEquity must be positive at barIndex="
                                + barIndex
                                + " value="
                                + totalEquity.toPlainString());
            }
            BigDecimal availableCapital =
                    totalEquity.subtract(position.positionNotional()).max(BigDecimal.ZERO);
            BigDecimal exposureRatio =
                    position.inPosition()
                            ? position
                                    .positionNotional()
                                    .divide(totalEquity, RATIO_SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
            snapshots.add(
                    new BacktestPortfolioSnapshot(
                            candles.get(barIndex).getOpenTime(),
                            initialCapital,
                            realizedPnl,
                            unrealizedPnl,
                            totalEquity,
                            availableCapital,
                            position,
                            exposureRatio));
        }

        if (activeTrade != null || nextTradeIndex != orderedTrades.size()) {
            throw invalidLedger("trade events were not fully consumed");
        }
        return List.copyOf(snapshots);
    }

    private BacktestPositionSnapshot openPosition(BacktestTrade trade, BigDecimal markPrice) {
        BigDecimal positionNotional = trade.getAmount().multiply(markPrice);
        BigDecimal unrealizedPnl =
                trade.getAmount().multiply(markPrice.subtract(trade.getEntryPrice()));
        return new BacktestPositionSnapshot(
                true,
                PositionSide.LONG,
                trade.getAmount(),
                trade.getEntryPrice(),
                markPrice,
                positionNotional,
                unrealizedPnl);
    }

    private BigDecimal settleExit(
            BigDecimal realizedPnl, BacktestTrade trade, BigDecimal feeRate) {
        BigDecimal pricePnl =
                trade.getAmount().multiply(trade.getExitPrice().subtract(trade.getEntryPrice()));
        BigDecimal exitFee =
                trade.getAmount().multiply(trade.getExitPrice()).multiply(feeRate);
        return realizedPnl.add(pricePnl).subtract(exitFee);
    }

    private void validateTrades(List<BacktestTrade> trades, int barCount) {
        int previousTradeNo = 0;
        for (BacktestTrade trade : trades) {
            Objects.requireNonNull(trade, "trade");
            if (trade.getTradeNo() <= previousTradeNo
                    || trade.getEntryIndex() < 0
                    || trade.getExitIndex() < trade.getEntryIndex()
                    || trade.getExitIndex() >= barCount
                    || trade.getPositionSide() != PositionSide.LONG) {
                throw invalidLedger("invalid tradeNo=" + trade.getTradeNo());
            }
            requirePositive(trade.getEntryPrice(), "entryPrice");
            requirePositive(trade.getExitPrice(), "exitPrice");
            requirePositive(trade.getAmount(), "amount");
            previousTradeNo = trade.getTradeNo();
        }
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw invalidLedger(field + " must be positive");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw invalidLedger(field + " must be non-negative");
        }
    }

    private BacktestException invalidLedger(String detail) {
        return new BacktestException("BACKTEST_EXECUTION_FAILED", "capital ledger: " + detail);
    }
}
