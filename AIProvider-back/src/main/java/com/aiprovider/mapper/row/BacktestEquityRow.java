package com.aiprovider.mapper.row;

import java.math.BigDecimal;

public class BacktestEquityRow {
    public long id, openTimeMs; public String runId; public int pointIndex; public BigDecimal equityRatio, drawdownRatio, equityValue, availableCapital, realizedPnl, unrealizedPnl, positionQuantity, positionNotional, exposureRatio; public boolean inPosition;
}
