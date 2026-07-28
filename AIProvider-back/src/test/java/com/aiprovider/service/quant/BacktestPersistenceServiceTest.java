package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantBacktestProperties;
import com.aiprovider.mapper.*;
import com.aiprovider.mapper.row.BacktestEquityRow;
import com.aiprovider.mapper.row.BacktestRunRow;
import com.aiprovider.quant.backtest.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;
import static org.mockito.ArgumentMatchers.any; import static org.mockito.Mockito.*; import static org.junit.jupiter.api.Assertions.*;

class BacktestPersistenceServiceTest {
    @Test void usesConfiguredEquityBatchesAndCompletesAfterAllWrites(){
        BacktestRunMapper runs=mock(BacktestRunMapper.class);BacktestTradeMapper trades=mock(BacktestTradeMapper.class);BacktestEquityMapper equity=mock(BacktestEquityMapper.class);QuantBacktestProperties p=new QuantBacktestProperties();p.setEquityInsertBatchSize(500);p.setTradeInsertBatchSize(500);when(runs.complete(any())).thenReturn(1);when(equity.insertBatch(any())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        List<EquityPoint> points=new ArrayList<>();for(int i=0;i<1201;i++)points.add(new EquityPoint(Instant.ofEpochMilli(i),BigDecimal.ONE,BigDecimal.ZERO,false,new BigDecimal("1000"),new BigDecimal("1000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        BacktestMetrics m=new BacktestMetrics(0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("1000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        BacktestResult result=new BacktestResult("EMA_CROSS_LONG_ONLY","1.0.0",Map.of("fastPeriod",12,"slowPeriod",26),"BTCUSDT",com.aiprovider.quant.market.model.KlineInterval.M1,1201,Instant.EPOCH,Instant.ofEpochMilli(1201*60000L),"TEST",BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("1000"),new BigDecimal("1000"),m,List.of(),points,List.of("中文 warning"));
        new BacktestPersistenceService(runs,trades,equity,new ObjectMapper(),p).persistCompleted("r",result);
        @SuppressWarnings("unchecked") var equityBatches = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(equity,times(3)).insertBatch(equityBatches.capture());
        BacktestEquityRow first = (BacktestEquityRow) equityBatches.getAllValues().get(0).get(0);
        assertEquals(new BigDecimal("1000"), first.equityValue);
        assertEquals(BigDecimal.ZERO, first.exposureRatio);
        var completed = org.mockito.ArgumentCaptor.forClass(BacktestRunRow.class);
        verify(runs).complete(completed.capture());
        assertEquals(new BigDecimal("1000"), completed.getValue().finalEquity);
        assertEquals(BigDecimal.ZERO, completed.getValue().totalPnl);
    }
}
