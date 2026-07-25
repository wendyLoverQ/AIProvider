package com.aiprovider.quant.backtest;

import com.aiprovider.quant.market.model.KlineInterval;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class BacktestResult {
    private final String strategyCode, strategyVersion, symbol, executionModel;
    private final Map<String,Integer> strategyParameters;
    private final KlineInterval interval;
    private final int barCount;
    private final Instant startOpenTime, endOpenTimeExclusive;
    private final BigDecimal feeRate, orderAmount;
    private final BacktestMetrics metrics;
    private final List<BacktestTrade> trades;
    private final List<EquityPoint> equityCurve;
    private final List<String> warnings;
    public BacktestResult(String code,String version,Map<String,Integer> params,String symbol,KlineInterval interval,int bars,Instant start,Instant end,String model,BigDecimal fee,BigDecimal amount,BacktestMetrics metrics,List<BacktestTrade> trades,List<EquityPoint> curve,List<String> warnings){this.strategyCode=code;this.strategyVersion=version;this.strategyParameters=Map.copyOf(params);this.symbol=symbol;this.interval=interval;this.barCount=bars;this.startOpenTime=start;this.endOpenTimeExclusive=end;this.executionModel=model;this.feeRate=fee;this.orderAmount=amount;this.metrics=metrics;this.trades=List.copyOf(trades);this.equityCurve=List.copyOf(curve);this.warnings=List.copyOf(warnings);}
    public String getStrategyCode(){return strategyCode;} public String getStrategyVersion(){return strategyVersion;} public Map<String,Integer> getStrategyParameters(){return strategyParameters;} public String getSymbol(){return symbol;} public KlineInterval getInterval(){return interval;} public int getBarCount(){return barCount;} public Instant getStartOpenTime(){return startOpenTime;} public Instant getEndOpenTimeExclusive(){return endOpenTimeExclusive;} public String getExecutionModel(){return executionModel;} public BigDecimal getFeeRate(){return feeRate;} public BigDecimal getOrderAmount(){return orderAmount;} public BacktestMetrics getMetrics(){return metrics;} public List<BacktestTrade> getTrades(){return trades;} public List<EquityPoint> getEquityCurve(){return equityCurve;} public List<String> getWarnings(){return warnings;}
}
