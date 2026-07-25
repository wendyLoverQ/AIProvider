package com.aiprovider.service.quant;

import com.aiprovider.mapper.BacktestEquityMapper;
import com.aiprovider.mapper.BacktestRunMapper;
import com.aiprovider.mapper.BacktestTradeMapper;
import com.aiprovider.quant.backtest.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.*;

@Service
public class BacktestPersistenceService {
    private final BacktestRunMapper runMapper; private final BacktestTradeMapper tradeMapper; private final BacktestEquityMapper equityMapper;
    private final ObjectMapper objectMapper; private final com.aiprovider.config.quant.QuantBacktestProperties properties;
    public BacktestPersistenceService(BacktestRunMapper r,BacktestTradeMapper t,BacktestEquityMapper e,ObjectMapper o,com.aiprovider.config.quant.QuantBacktestProperties p){runMapper=r;tradeMapper=t;equityMapper=e;objectMapper=o;properties=p;}
    @Transactional
    public void persistCompleted(String runId, BacktestResult result) {
        try {
            Map<String,Object> row = new HashMap<>(); row.put("runId",runId); row.put("resolvedJson",objectMapper.writeValueAsString(result.getStrategyParameters()));
            BacktestMetrics m=result.getMetrics(); row.put("barCount",result.getBarCount()); row.put("tradeCount",m.getTradeCount()); row.put("winningTradeCount",m.getWinningTradeCount()); row.put("losingTradeCount",m.getLosingTradeCount()); row.put("breakEvenTradeCount",m.getBreakEvenTradeCount());
            row.put("winRate",m.getWinRate()); row.put("grossProfit",m.getGrossProfit()); row.put("grossLoss",m.getGrossLoss()); row.put("netProfit",m.getNetProfit()); row.put("totalReturnRatio",m.getTotalReturnRatio()); row.put("maximumDrawdownRatio",m.getMaximumDrawdownRatio()); row.put("profitFactor",m.getProfitFactor()); row.put("averageTradeReturnRatio",m.getAverageTradeReturnRatio()); row.put("buyAndHoldReturnRatio",m.getBuyAndHoldReturnRatio()); row.put("totalFees",m.getTotalFees()); row.put("executionModel",result.getExecutionModel()); row.put("warningsJson",objectMapper.writeValueAsString(result.getWarnings())); row.put("equityPointCount",result.getEquityCurve().size()); row.put("now",Instant.now());
            for(int i=0;i<result.getTrades().size();i+=properties.getTradeInsertBatchSize()) tradeMapper.insertBatch(tradeRows(runId,result.getTrades().subList(i,Math.min(i+properties.getTradeInsertBatchSize(),result.getTrades().size()))));
            for(int i=0;i<result.getEquityCurve().size();i+=properties.getEquityInsertBatchSize()) equityMapper.insertBatch(equityRows(runId,result.getEquityCurve().subList(i,Math.min(i+properties.getEquityInsertBatchSize(),result.getEquityCurve().size())),i));
            if(runMapper.complete(row)!=1) throw new BacktestTaskException("BACKTEST_PERSISTENCE_FAILED","runId="+runId+" completion update affected 0 rows");
        } catch (BacktestTaskException e) { throw e; } catch (Exception e) { throw new BacktestTaskException("BACKTEST_PERSISTENCE_FAILED","runId="+runId+" result persistence failed",e); }
    }
    private List<Map<String,Object>> tradeRows(String id,List<BacktestTrade> list){List<Map<String,Object>> out=new ArrayList<>();for(BacktestTrade t:list){Map<String,Object> r=new HashMap<>();r.put("runId",id);r.put("tradeNo",t.getTradeNo());r.put("entrySignalIndex",t.getEntrySignalIndex());r.put("entryIndex",t.getEntryIndex());r.put("entryTimeMs",t.getEntryTime().toEpochMilli());r.put("entryPrice",t.getEntryPrice());r.put("exitSignalIndex",t.getExitSignalIndex());r.put("exitIndex",t.getExitIndex());r.put("exitTimeMs",t.getExitTime().toEpochMilli());r.put("exitPrice",t.getExitPrice());r.put("amount",t.getAmount());r.put("grossProfit",t.getGrossProfit());r.put("fee",t.getFee());r.put("netProfit",t.getNetProfit());r.put("returnRatio",t.getReturnRatio());r.put("barsHeld",t.getBarsHeld());r.put("forcedExit",t.isForcedExit());r.put("exitReason",t.getExitReason());out.add(r);}return out;}
    private List<Map<String,Object>> equityRows(String id,List<EquityPoint> list,int offset){List<Map<String,Object>> out=new ArrayList<>();for(int i=0;i<list.size();i++){EquityPoint p=list.get(i);Map<String,Object> r=new HashMap<>();r.put("runId",id);r.put("pointIndex",offset+i);r.put("openTimeMs",p.openTime().toEpochMilli());r.put("equityRatio",p.equityRatio());r.put("drawdownRatio",p.drawdownRatio());r.put("inPosition",p.inPosition());out.add(r);}return out;}
}
