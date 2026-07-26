package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.*;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.*;
import com.aiprovider.quant.backtest.*;
import com.aiprovider.quant.market.history.model.MarketDataSnapshot;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.market.history.service.MarketDataSnapshotException;
import com.aiprovider.quant.market.history.service.MarketDataSnapshotService;
import com.aiprovider.quant.strategy.*;
import com.aiprovider.service.quant.model.BacktestRunCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BacktestRunService {
    private static final long MAX_QUERY_OFFSET = 10_000_000L;
    private final BacktestRunMapper runs; private final BacktestTradeMapper trades; private final BacktestEquityMapper equity;
    private final MarketDatasetRepository datasets; private final MarketDataSnapshotService snapshots; private final BacktestEngine engine;
    private final StrategyRegistry strategies; private final BacktestPersistenceService persistence; private final BacktestFailureService failures;
    private final ThreadPoolExecutor executor; private final ObjectMapper json;
    public BacktestRunService(BacktestRunMapper r, BacktestTradeMapper t, BacktestEquityMapper e, MarketDatasetRepository d,
            MarketDataSnapshotService s, BacktestEngine b, StrategyRegistry g, BacktestPersistenceService p,
            BacktestFailureService f, @Qualifier("quantBacktestExecutor") ThreadPoolExecutor x, ObjectMapper j) {
        runs=r; trades=t; equity=e; datasets=d; snapshots=s; engine=b; strategies=g; persistence=p; failures=f; executor=x; json=j;
    }
    public String create(BacktestCreateRequest q) {
        return createWithRunId(UUID.randomUUID().toString(), q);
    }
    String createWithRunId(String id, BacktestCreateRequest q) {
        try { UUID.fromString(id); } catch (IllegalArgumentException e) { throw error("BACKTEST_RUN_ID_CONFLICT","runId must be a UUID"); }
        validate(q); String code=q.getStrategyCode().trim(), version=q.getStrategyVersion().trim();
        Map<String,Integer> params=q.getStrategyParameters()==null?Map.of():Map.copyOf(q.getStrategyParameters());
        var dataset=datasets.findById(q.getDatasetId()); if(dataset==null) throw error("BACKTEST_DATASET_NOT_FOUND","datasetId="+q.getDatasetId()+" not found");
        QuantStrategyDefinition def=definition(code); if(!def.version().equals(version)) throw error("BACKTEST_STRATEGY_VERSION_NOT_SUPPORTED","strategyCode="+code+" version="+version);
        try { def.minimumRequiredBars(params); } catch(StrategyException e) { throw error(e.getErrorCode(),e.getMessage()); }
        BacktestRunRow existing=runs.findByRunId(id);
        if(existing!=null) {
            boolean same=existing.datasetId==q.getDatasetId() && existing.startOpenTimeMs==q.getStartOpenTimeInclusive().toEpochMilli()
                    && existing.endOpenTimeExclusiveMs==q.getEndOpenTimeExclusive().toEpochMilli() && code.equals(existing.strategyCode)
                    && version.equals(existing.strategyVersion) && Objects.equals(read(existing.requestedParametersJson),params)
                    && existing.orderAmount != null && existing.orderAmount.compareTo(q.getOrderAmount()) == 0
                    && existing.feeRate != null && existing.feeRate.compareTo(q.getFeeRate()) == 0
                    && existing.forceCloseAtEnd==q.isForceCloseAtEnd();
            if(!same) throw error("BACKTEST_RUN_ID_CONFLICT","runId already belongs to a different request");
            return id;
        }
        BacktestRunRow row=new BacktestRunRow(); row.runId=id; row.datasetId=q.getDatasetId();
        row.provider=dataset.getProvider().name(); row.marketType=dataset.getMarketType().name(); row.dataType=dataset.getDataType().name(); row.symbol=dataset.getSymbol(); row.intervalCode=dataset.getInterval().code();
        row.startOpenTimeMs=q.getStartOpenTimeInclusive().toEpochMilli(); row.endOpenTimeExclusiveMs=q.getEndOpenTimeExclusive().toEpochMilli(); row.strategyCode=code; row.strategyVersion=version;
        row.requestedParametersJson=write(params); row.orderAmount=q.getOrderAmount(); row.feeRate=q.getFeeRate(); row.forceCloseAtEnd=q.isForceCloseAtEnd(); row.queuedAt=Instant.now(); row.updatedAt=row.queuedAt;
        try {
            if (runs.insert(row) != 1) {
                throw error("BACKTEST_RUN_PERSISTENCE_FAILED", "run insert affected an unexpected number of rows");
            }
        } catch (DataIntegrityViolationException exception) {
            BacktestRunRow raced = runs.findByRunId(id);
            if (raced == null) {
                throw exception;
            }
            if (!sameRequest(raced, q, code, version, params)) {
                throw error("BACKTEST_RUN_ID_CONFLICT", "runId already belongs to a different request");
            }
            return id;
        }
        BacktestRunCommand command=new BacktestRunCommand(id,row.datasetId,q.getStartOpenTimeInclusive(),q.getEndOpenTimeExclusive(),code,version,params,q.getOrderAmount(),q.getFeeRate(),q.isForceCloseAtEnd());
        try { executor.execute(() -> run(command)); } catch(RejectedExecutionException ex) { failSafely(id,"BACKTEST_QUEUE_FULL",descriptor(ex.getMessage())); throw error("BACKTEST_QUEUE_FULL","runId="+id+" queue is full"); }
        return id;
    }
    private boolean sameRequest(BacktestRunRow existing, BacktestCreateRequest q, String code, String version, Map<String,Integer> params) {
        return existing.datasetId == q.getDatasetId()
                && existing.startOpenTimeMs == q.getStartOpenTimeInclusive().toEpochMilli()
                && existing.endOpenTimeExclusiveMs == q.getEndOpenTimeExclusive().toEpochMilli()
                && code.equals(existing.strategyCode)
                && version.equals(existing.strategyVersion)
                && Objects.equals(read(existing.requestedParametersJson), params)
                && existing.orderAmount != null && existing.orderAmount.compareTo(q.getOrderAmount()) == 0
                && existing.feeRate != null && existing.feeRate.compareTo(q.getFeeRate()) == 0
                && existing.forceCloseAtEnd == q.isForceCloseAtEnd();
    }
    private void run(BacktestRunCommand c) {
        try {
            cas(c.runId(),BacktestRunStatus.QUEUED,BacktestRunStatus.LOADING_SNAPSHOT,BigDecimal.TEN);
            MarketDataSnapshot s=snapshots.load(c.datasetId(),c.startOpenTimeInclusive(),c.endOpenTimeExclusive());
            BacktestRunRow meta=new BacktestRunRow(); meta.runId=c.runId(); meta.datasetLastValidatedAt=s.getDatasetLastValidatedAt(); meta.datasetLastSyncTaskId=s.getDatasetLastSyncTaskId(); meta.provider=s.getProvider().name(); meta.marketType=s.getMarketType().name(); meta.dataType=s.getDataType().name(); meta.symbol=s.getSymbol(); meta.intervalCode=s.getInterval().code(); meta.barCount=(int)s.getActualCandleCount(); meta.updatedAt=Instant.now();
            if(runs.updateSnapshot(meta)!=1) throw error("BACKTEST_STATE_CONFLICT","runId="+c.runId()+" snapshot update failed");
            cas(c.runId(),BacktestRunStatus.LOADING_SNAPSHOT,BacktestRunStatus.RUNNING_ENGINE,new BigDecimal("30"));
            BacktestResult result=engine.run(new BacktestRequest(c.strategyCode(),c.strategyVersion(),c.strategyParameters(),c.orderAmount(),c.feeRate(),c.forceCloseAtEnd()),s.getSymbol(),s.getInterval(),s.getCandles());
            validateResult(c.runId(),s,result); cas(c.runId(),BacktestRunStatus.RUNNING_ENGINE,BacktestRunStatus.PERSISTING,new BigDecimal("80")); persistence.persistCompleted(c.runId(),result);
        } catch(MarketDataSnapshotException e) { failSafely(c.runId(),"BACKTEST_SNAPSHOT_INVALID","runId="+c.runId()+" snapshotCode="+e.getErrorCode()+" "+e.getMessage());
        } catch(BacktestException|StrategyException|BacktestTaskException e) { failSafely(c.runId(),e instanceof BacktestException?((BacktestException)e).getErrorCode():e instanceof StrategyException?((StrategyException)e).getErrorCode():((BacktestTaskException)e).getErrorCode(),descriptor(e.getMessage()));
        } catch(RuntimeException e) { failSafely(c.runId(),"BACKTEST_EXECUTION_FAILED",descriptor(e.getMessage())); }
    }
    private void failSafely(String id,String code,String message){ try{ failures.markFailed(id,code,message); } catch(RuntimeException ignored){ } }
    private String descriptor(String message){String s=message==null?"backtest failed":message.replaceAll("[\\r\\n]"," "); return s.substring(0,Math.min(1000,s.length()));}
    private void cas(String id,BacktestRunStatus from,BacktestRunStatus to,BigDecimal progress){if(runs.transition(id,from.name(),to.name(),progress,Instant.now())!=1)throw error("BACKTEST_STATE_CONFLICT","runId="+id+" transition failed");}
    private void validateResult(String id,MarketDataSnapshot s,BacktestResult r){if(r.getBarCount()!=s.getActualCandleCount()||!s.getSymbol().equals(r.getSymbol())||s.getInterval()!=r.getInterval()||r.getTrades().size()!=r.getMetrics().getTradeCount()||r.getEquityCurve().size()!=r.getBarCount()||r.getMetrics().getMaximumDrawdownRatio().signum()<0||r.getEquityCurve().isEmpty())throw error("BACKTEST_RESULT_INVALID","runId="+id+" result consistency check failed");}
    public List<BacktestDtos.Strategy> strategies(){return strategies.list().stream().map(d->new BacktestDtos.Strategy(d.code(),d.name(),d.version(),d.description(),d.minimumRequiredBars(Map.of()),d.parameters().stream().map(p->new BacktestDtos.Parameter(p.name(),p.defaultValue(),p.minValue(),p.maxValue())).toList())).toList();}
    public BacktestDtos.Page<BacktestDtos.RunDetail> page(int page,int size,String status,String symbol,String code){if(page<1||size<1||size>100)throw error("BACKTEST_REQUEST_INVALID","page/pageSize invalid");long offset=calculateOffset(page,size);String s=status==null||status.isBlank()?null:status.trim().toUpperCase(Locale.ROOT);String sym=symbol==null||symbol.isBlank()?null:symbol.trim().toUpperCase(Locale.ROOT);String strategy=code==null||code.isBlank()?null:code.trim();return new BacktestDtos.Page<>(runs.findPage(s,sym,strategy,size,offset).stream().map(this::detail).toList(),runs.count(s,sym,strategy),page,size);}
    public List<BacktestDtos.RunDetail> nonTerminal(){return runs.findNonTerminal().stream().map(this::detail).toList();}
    public BacktestDtos.RunDetail get(String id){return detail(require(id));}
    public BacktestDtos.Page<BacktestDtos.Trade> trades(String id,int page,int size){BacktestRunRow run=require(id);if(page<1||size<1||size>500)throw error("BACKTEST_REQUEST_INVALID","page/pageSize invalid");if(!BacktestRunStatus.COMPLETED.name().equals(run.status))return new BacktestDtos.Page<>(List.of(),0,page,size);return new BacktestDtos.Page<>(trades.findPage(id,size,calculateOffset(page,size)).stream().map(this::trade).toList(),trades.count(id),page,size);}
    public BacktestDtos.Equity equity(String id,int max){require(id);if(max<100||max>5000)throw error("BACKTEST_REQUEST_INVALID","maxPoints must be 100..5000");int total=equity.count(id);if(total==0)return new BacktestDtos.Equity(false,0,List.of());List<BacktestEquityRow> rows=total<=max?equity.findAll(id):equity.findAtIndices(id,BacktestEquitySampler.indices(total,max));return new BacktestDtos.Equity(total>max,total,rows.stream().map(this::point).toList());}
    private static long calculateOffset(int page,int size){try{long offset=Math.multiplyExact((long)page-1L,(long)size);if(offset>MAX_QUERY_OFFSET)throw new BacktestTaskException("BACKTEST_REQUEST_INVALID","page offset exceeds limit");return offset;}catch(ArithmeticException e){throw new BacktestTaskException("BACKTEST_REQUEST_INVALID","page offset overflow",e);}}
    private void validate(BacktestCreateRequest q){if(q==null||q.getDatasetId()<=0||q.getStartOpenTimeInclusive()==null||q.getEndOpenTimeExclusive()==null||!q.getStartOpenTimeInclusive().isBefore(q.getEndOpenTimeExclusive())||q.getStrategyCode()==null||q.getStrategyCode().isBlank()||q.getStrategyVersion()==null||q.getStrategyVersion().isBlank()||q.getOrderAmount()==null||!fitsDecimal38_18(q.getOrderAmount())||q.getOrderAmount().signum()<=0||q.getFeeRate()==null||!fitsDecimal38_18(q.getFeeRate())||q.getFeeRate().signum()<0||q.getFeeRate().compareTo(new BigDecimal("0.01"))>0||!q.isForceCloseAtEnd()||q.getStrategyParameters()!=null&&q.getStrategyParameters().entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null))throw error("BACKTEST_REQUEST_INVALID","invalid backtest request");}
    private static boolean fitsDecimal38_18(BigDecimal v){if(v==null||v.scale()>18||v.precision()>38)return false;BigDecimal n=v.stripTrailingZeros();return Math.max(0,n.precision()-n.scale())<=20;}
    private BacktestRunRow require(String id){BacktestRunRow r=runs.findByRunId(id);if(r==null)throw error("BACKTEST_RUN_NOT_FOUND","runId="+id+" not found");return r;}
    private QuantStrategyDefinition definition(String code){try{return strategies.get(code==null?null:code.trim());}catch(StrategyException e){throw error(e.getErrorCode(),e.getMessage());}}
    private BacktestTaskException error(String c,String m){return new BacktestTaskException(c,m);} private String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw error("BACKTEST_REQUEST_INVALID","parameters cannot be serialized");}}
    private BacktestDtos.RunDetail detail(BacktestRunRow r){Map<String,Integer> req=read(r.requestedParametersJson),res=r.resolvedParametersJson==null?Map.of():read(r.resolvedParametersJson);Map<String,Object> m=new LinkedHashMap<>();m.put("winRate",r.winRate);m.put("grossProfit",r.grossProfit);m.put("grossLoss",r.grossLoss);m.put("netProfit",r.netProfit);m.put("totalReturnRatio",r.totalReturnRatio);m.put("maximumDrawdownRatio",r.maximumDrawdownRatio);m.put("profitFactor",r.profitFactor);m.put("averageTradeReturnRatio",r.averageTradeReturnRatio);m.put("buyAndHoldReturnRatio",r.buyAndHoldReturnRatio);m.put("totalFees",r.totalFees);m.put("_executionModel",r.executionModel);m.put("_warnings",readWarnings(r.warningsJson));return new BacktestDtos.RunDetail(r.runId,r.datasetId,r.datasetLastValidatedAt,r.datasetLastSyncTaskId,r.provider,r.marketType,r.dataType,r.symbol,r.intervalCode,Instant.ofEpochMilli(r.startOpenTimeMs),Instant.ofEpochMilli(r.endOpenTimeExclusiveMs),r.strategyCode,r.strategyVersion,req,res,r.orderAmount,r.feeRate,r.forceCloseAtEnd,r.status,r.progressPercent,r.errorCode,r.errorMessage,r.queuedAt,r.startedAt,r.finishedAt,r.barCount,r.tradeCount,r.winningTradeCount,r.losingTradeCount,r.breakEvenTradeCount,m,r.equityPointCount==null?0:r.equityPointCount);}
    private BacktestDtos.Trade trade(BacktestTradeRow r){return new BacktestDtos.Trade(r.tradeNo,r.entrySignalIndex,r.entryIndex,Instant.ofEpochMilli(r.entryTimeMs),r.entryPrice,r.exitSignalIndex,r.exitIndex,Instant.ofEpochMilli(r.exitTimeMs),r.exitPrice,r.amount,r.grossProfit,r.fee,r.netProfit,r.returnRatio,r.barsHeld,r.forcedExit,r.exitReason);} private BacktestDtos.EquityPoint point(BacktestEquityRow r){return new BacktestDtos.EquityPoint(r.pointIndex,Instant.ofEpochMilli(r.openTimeMs),r.equityRatio,r.drawdownRatio,r.inPosition);} private Map<String,Integer> read(String s){try{return json.readValue(s,new TypeReference<Map<String,Integer>>(){});}catch(Exception e){throw error("BACKTEST_PERSISTENCE_FAILED","stored JSON is invalid");}} private List<String> readWarnings(String s){if(s==null||s.isBlank())return List.of();try{return List.copyOf(json.readValue(s,new TypeReference<List<String>>(){}));}catch(Exception e){throw error("BACKTEST_PERSISTENCE_FAILED","stored warnings JSON is invalid");}}
    public void resubmitQueued(BacktestRunRow row) { try { BacktestRunCommand c=new BacktestRunCommand(row.runId,row.datasetId,Instant.ofEpochMilli(row.startOpenTimeMs),Instant.ofEpochMilli(row.endOpenTimeExclusiveMs),row.strategyCode,row.strategyVersion,read(row.requestedParametersJson),row.orderAmount,row.feeRate,row.forceCloseAtEnd); executor.execute(() -> run(c)); } catch (RuntimeException e) { failSafely(row.runId,"BACKTEST_QUEUE_FULL",descriptor(e.getMessage())); } }
    public void markInterruptedOnRestart(String runId, String previousStatus) { failSafely(runId,"BACKTEST_INTERRUPTED_BY_RESTART","previousStatus="+previousStatus+" interrupted by application restart"); }
}
