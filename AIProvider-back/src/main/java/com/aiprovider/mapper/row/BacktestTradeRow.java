package com.aiprovider.mapper.row;

import java.math.BigDecimal;

public class BacktestTradeRow {
    public long id, runIdHash; public String runId, exitReason; public int tradeNo, entrySignalIndex, entryIndex, exitIndex, barsHeld; public long entryTimeMs, exitTimeMs;
    public Integer exitSignalIndex; public BigDecimal entryPrice, exitPrice, amount, grossProfit, fee, netProfit, returnRatio; public boolean forcedExit;
}
