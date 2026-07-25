package com.aiprovider.quant.backtest;

import java.math.BigDecimal;

public final class BacktestMetrics {
    private final int tradeCount, winningTradeCount, losingTradeCount, breakEvenTradeCount;
    private final BigDecimal winRate, grossProfit, grossLoss, netProfit, totalReturnRatio, maximumDrawdownRatio, profitFactor, averageTradeReturnRatio, buyAndHoldReturnRatio, totalFees;
    public BacktestMetrics(int count,int wins,int losses,int breaks,BigDecimal winRate,BigDecimal grossProfit,BigDecimal grossLoss,BigDecimal netProfit,BigDecimal totalReturn,BigDecimal drawdown,BigDecimal profitFactor,BigDecimal average,BigDecimal buyHold,BigDecimal fees){this.tradeCount=count;this.winningTradeCount=wins;this.losingTradeCount=losses;this.breakEvenTradeCount=breaks;this.winRate=winRate;this.grossProfit=grossProfit;this.grossLoss=grossLoss;this.netProfit=netProfit;this.totalReturnRatio=totalReturn;this.maximumDrawdownRatio=drawdown;this.profitFactor=profitFactor;this.averageTradeReturnRatio=average;this.buyAndHoldReturnRatio=buyHold;this.totalFees=fees;}
    public int getTradeCount(){return tradeCount;} public int getWinningTradeCount(){return winningTradeCount;} public int getLosingTradeCount(){return losingTradeCount;} public int getBreakEvenTradeCount(){return breakEvenTradeCount;} public BigDecimal getWinRate(){return winRate;} public BigDecimal getGrossProfit(){return grossProfit;} public BigDecimal getGrossLoss(){return grossLoss;} public BigDecimal getNetProfit(){return netProfit;} public BigDecimal getTotalReturnRatio(){return totalReturnRatio;} public BigDecimal getMaximumDrawdownRatio(){return maximumDrawdownRatio;} public BigDecimal getProfitFactor(){return profitFactor;} public BigDecimal getAverageTradeReturnRatio(){return averageTradeReturnRatio;} public BigDecimal getBuyAndHoldReturnRatio(){return buyAndHoldReturnRatio;} public BigDecimal getTotalFees(){return totalFees;}
}
