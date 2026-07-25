package com.aiprovider.quant.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public final class BacktestTrade {
    private final int tradeNo, entrySignalIndex, entryIndex, exitIndex, barsHeld;
    private final Integer exitSignalIndex;
    private final Instant entryTime, exitTime;
    private final BigDecimal entryPrice, exitPrice, amount, grossProfit, fee, netProfit, returnRatio;
    private final boolean forcedExit;
    private final String exitReason;
    public BacktestTrade(int no, int entrySignal, int entry, Instant entryTime, BigDecimal entryPrice, Integer exitSignal, int exitIndex, Instant exitTime, BigDecimal exitPrice, BigDecimal amount, BigDecimal gross, BigDecimal fee, BigDecimal net, BigDecimal ratio, int held, boolean forced, String reason) {
        this.tradeNo=no; this.entrySignalIndex=entrySignal; this.entryIndex=entry; this.entryTime=entryTime; this.entryPrice=entryPrice; this.exitSignalIndex=exitSignal; this.exitIndex=exitIndex; this.exitTime=exitTime; this.exitPrice=exitPrice; this.amount=amount; this.grossProfit=gross; this.fee=fee; this.netProfit=net; this.returnRatio=ratio; this.barsHeld=held; this.forcedExit=forced; this.exitReason=reason;
    }
    public int getTradeNo(){return tradeNo;} public int getEntrySignalIndex(){return entrySignalIndex;} public int getEntryIndex(){return entryIndex;} public Instant getEntryTime(){return entryTime;} public BigDecimal getEntryPrice(){return entryPrice;} public Integer getExitSignalIndex(){return exitSignalIndex;} public int getExitIndex(){return exitIndex;} public Instant getExitTime(){return exitTime;} public BigDecimal getExitPrice(){return exitPrice;} public BigDecimal getAmount(){return amount;} public BigDecimal getGrossProfit(){return grossProfit;} public BigDecimal getFee(){return fee;} public BigDecimal getNetProfit(){return netProfit;} public BigDecimal getReturnRatio(){return returnRatio;} public int getBarsHeld(){return barsHeld;} public boolean isForcedExit(){return forcedExit;} public String getExitReason(){return exitReason;}
}
