package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestRunFixedIdTest {
    @Test void sameImmutableRequestIsIdempotentAndDifferentRequestConflicts(){
        BacktestRunMapper runs=mock(BacktestRunMapper.class); BacktestRunRow existing=new BacktestRunRow();
        existing.runId="00000000-0000-0000-0000-000000000001";existing.datasetId=1;existing.startOpenTimeMs=0;existing.endOpenTimeExclusiveMs=60000;existing.strategyCode="EMA_CROSS_LONG_ONLY";existing.strategyVersion="1.0.0";existing.requestedParametersJson="{\"fastPeriod\":5,\"slowPeriod\":20}";existing.orderAmount=new BigDecimal("1.0");existing.feeRate=new BigDecimal("0.001");existing.forceCloseAtEnd=true;
        when(runs.findByRunId(existing.runId)).thenReturn(existing);
        MarketDatasetRepository datasets=mock(MarketDatasetRepository.class);when(datasets.findById(1)).thenReturn(new com.aiprovider.quant.market.history.model.MarketDataset());
        ThreadPoolExecutor executor=mock(ThreadPoolExecutor.class);
        com.aiprovider.quant.market.history.service.MarketDataSnapshotService snapshots=new com.aiprovider.quant.market.history.service.MarketDataSnapshotService(datasets,mock(com.aiprovider.quant.market.history.port.MarketCandleRepository.class),100);
        BacktestRunService service=new BacktestRunService(runs,mock(BacktestTradeMapper.class),mock(BacktestEquityMapper.class),datasets,snapshots,new com.aiprovider.quant.backtest.BacktestEngine(new StrategyRegistry()),new StrategyRegistry(),mock(BacktestPersistenceService.class),mock(BacktestFailureService.class),executor,new ObjectMapper());
        BacktestCreateRequest request=request();
        assertEquals(existing.runId,service.createWithRunId(existing.runId,request));
        verify(runs,never()).insert(any());verifyNoInteractions(executor);
        request.setOrderAmount(new BigDecimal("2"));BacktestTaskException conflict=assertThrows(BacktestTaskException.class,()->service.createWithRunId(existing.runId,request));assertEquals("BACKTEST_RUN_ID_CONFLICT",conflict.getErrorCode());
    }
    private BacktestCreateRequest request(){BacktestCreateRequest q=new BacktestCreateRequest();q.setDatasetId(1);q.setStartOpenTimeInclusive(Instant.EPOCH);q.setEndOpenTimeExclusive(Instant.ofEpochMilli(60000));q.setStrategyCode("EMA_CROSS_LONG_ONLY");q.setStrategyVersion("1.0.0");q.setStrategyParameters(Map.of("fastPeriod",5,"slowPeriod",20));q.setOrderAmount(new BigDecimal("1"));q.setFeeRate(new BigDecimal("0.001"));q.setForceCloseAtEnd(true);return q;}
}
