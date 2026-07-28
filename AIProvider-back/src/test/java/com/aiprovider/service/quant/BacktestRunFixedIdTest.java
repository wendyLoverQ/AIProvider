package com.aiprovider.service.quant;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.quant.market.history.port.MarketDatasetRepository;
import com.aiprovider.quant.strategy.StrategyRegistry;
import com.aiprovider.quant.execution.*;
import com.aiprovider.service.quant.model.BacktestRunCommand;
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
        existing.runId="00000000-0000-0000-0000-000000000001";existing.datasetId=1;existing.startOpenTimeMs=0;existing.endOpenTimeExclusiveMs=60000;existing.strategyCode="EMA_CROSS_LONG_ONLY";existing.strategyVersion="1.0.0";existing.executionProfileCode="USDM_PERPETUAL_LONG_ONLY_1X_V1";existing.directionMode="LONG_ONLY";existing.orderSizingMode="BASE_QUANTITY";existing.requestedParametersJson="{\"fastPeriod\":5,\"slowPeriod\":20}";existing.initialCapital=new BigDecimal("1000");existing.orderAmount=new BigDecimal("1.0");existing.feeRate=new BigDecimal("0.001");existing.forceCloseAtEnd=true;
        when(runs.findByRunId(existing.runId)).thenReturn(existing);
        MarketDatasetRepository datasets=mock(MarketDatasetRepository.class);var dataset=new com.aiprovider.quant.market.history.model.MarketDataset();dataset.setProvider(com.aiprovider.quant.market.model.MarketProviderId.BINANCE_USDM);dataset.setMarketType(com.aiprovider.quant.market.model.MarketType.USDM_PERPETUAL);dataset.setDataType(com.aiprovider.quant.market.history.model.MarketDataType.CANDLE);dataset.setSymbol("BTCUSDT");dataset.setInterval(com.aiprovider.quant.market.model.KlineInterval.M1);when(datasets.findById(1)).thenReturn(dataset);
        ThreadPoolExecutor executor=mock(ThreadPoolExecutor.class);
        com.aiprovider.quant.market.history.service.MarketDataSnapshotService snapshots=new com.aiprovider.quant.market.history.service.MarketDataSnapshotService(datasets,mock(com.aiprovider.quant.market.history.port.MarketCandleRepository.class),100);
        BacktestRunService service=new BacktestRunService(runs,mock(BacktestTradeMapper.class),mock(BacktestEquityMapper.class),datasets,snapshots,new com.aiprovider.quant.backtest.BacktestEngine(new StrategyRegistry()),new StrategyRegistry(),new BacktestCompatibilityService(new ExecutionProfileRegistry()),mock(BacktestPersistenceService.class),mock(BacktestFailureService.class),executor,new ObjectMapper());
        BacktestCreateRequest request=request();
        assertEquals(existing.runId,service.createWithRunId(existing.runId,request));
        verify(runs,never()).insert(any());verifyNoInteractions(executor);
        request.setOrderAmount(new BigDecimal("2"));BacktestTaskException conflict=assertThrows(BacktestTaskException.class,()->service.createWithRunId(existing.runId,request));assertEquals("BACKTEST_RUN_ID_CONFLICT",conflict.getErrorCode());
        request.setOrderAmount(BigDecimal.ONE);request.setDirectionMode("long_only");BacktestTaskException executionConflict=assertThrows(BacktestTaskException.class,()->service.createWithRunId(existing.runId,request));assertEquals("BACKTEST_RUN_ID_CONFLICT",executionConflict.getErrorCode());
    }

    @Test
    void recoveryCommandPreservesAllExecutionFieldsAndCorruptionKeepsItsRealErrorCode() {
        BacktestFailureService failures = mock(BacktestFailureService.class);
        ThreadPoolExecutor executor = mock(ThreadPoolExecutor.class);
        BacktestRunService service = service(mock(BacktestRunMapper.class), failures, executor);
        BacktestRunRow row = recoveryRow();

        BacktestRunCommand command = service.recoveryCommand(row);
        assertEquals(ExecutionProfileCode.USDM_PERPETUAL_LONG_ONLY_1X_V1, command.executionProfileCode());
        assertEquals(DirectionMode.LONG_ONLY, command.directionMode());
        assertEquals(OrderSizingMode.BASE_QUANTITY, command.orderSizingMode());
        assertEquals(new BigDecimal("1000"), command.initialCapital());

        row.directionMode = "long_only";
        service.resubmitQueued(row);
        verify(failures)
            .markFailed(
                eq(row.runId),
                eq("BACKTEST_DIRECTION_INCOMPATIBLE"),
                contains("stored directionMode=long_only"));
        verifyNoInteractions(executor);
    }

    private BacktestRunService service(
        BacktestRunMapper runs, BacktestFailureService failures, ThreadPoolExecutor executor) {
        MarketDatasetRepository datasets = mock(MarketDatasetRepository.class);
        return new BacktestRunService(
            runs,
            mock(BacktestTradeMapper.class),
            mock(BacktestEquityMapper.class),
            datasets,
            new com.aiprovider.quant.market.history.service.MarketDataSnapshotService(
                datasets,
                mock(com.aiprovider.quant.market.history.port.MarketCandleRepository.class),
                100),
            new com.aiprovider.quant.backtest.BacktestEngine(new StrategyRegistry()),
            new StrategyRegistry(),
            new BacktestCompatibilityService(new ExecutionProfileRegistry()),
            mock(BacktestPersistenceService.class),
            failures,
            executor,
            new ObjectMapper());
    }

    private BacktestRunRow recoveryRow() {
        BacktestRunRow row = new BacktestRunRow();
        row.runId = "00000000-0000-0000-0000-000000000002";
        row.datasetId = 1;
        row.startOpenTimeMs = 0;
        row.endOpenTimeExclusiveMs = 60_000;
        row.strategyCode = "EMA_CROSS_LONG_ONLY";
        row.strategyVersion = "1.0.0";
        row.executionProfileCode = "USDM_PERPETUAL_LONG_ONLY_1X_V1";
        row.directionMode = "LONG_ONLY";
        row.orderSizingMode = "BASE_QUANTITY";
        row.requestedParametersJson = "{\"fastPeriod\":5,\"slowPeriod\":20}";
        row.initialCapital = new BigDecimal("1000");
        row.orderAmount = BigDecimal.ONE;
        row.feeRate = BigDecimal.ZERO;
        row.forceCloseAtEnd = true;
        return row;
    }
    private BacktestCreateRequest request(){BacktestCreateRequest q=new BacktestCreateRequest();q.setDatasetId(1);q.setStartOpenTimeInclusive(Instant.EPOCH);q.setEndOpenTimeExclusive(Instant.ofEpochMilli(60000));q.setStrategyCode("EMA_CROSS_LONG_ONLY");q.setStrategyVersion("1.0.0");q.setExecutionProfileCode("USDM_PERPETUAL_LONG_ONLY_1X_V1");q.setDirectionMode("LONG_ONLY");q.setOrderSizingMode("BASE_QUANTITY");q.setStrategyParameters(Map.of("fastPeriod",5,"slowPeriod",20));q.setInitialCapital(new BigDecimal("1000"));q.setOrderAmount(new BigDecimal("1"));q.setFeeRate(new BigDecimal("0.001"));q.setForceCloseAtEnd(true);return q;}
}
