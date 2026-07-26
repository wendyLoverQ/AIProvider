package com.aiprovider.service.quant;

import com.aiprovider.config.quant.QuantBacktestProperties;
import com.aiprovider.mapper.*;
import com.aiprovider.quant.backtest.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal; import java.time.Instant; import java.util.*;
import static org.mockito.ArgumentMatchers.any; import static org.mockito.Mockito.*; import static org.junit.jupiter.api.Assertions.*;

class BacktestPersistenceServiceTest {
    @Test void usesConfiguredEquityBatchesAndCompletesAfterAllWrites(){
        BacktestRunMapper runs=mock(BacktestRunMapper.class);BacktestTradeMapper trades=mock(BacktestTradeMapper.class);BacktestEquityMapper equity=mock(BacktestEquityMapper.class);QuantBacktestProperties p=new QuantBacktestProperties();p.setEquityInsertBatchSize(500);p.setTradeInsertBatchSize(500);when(runs.complete(any())).thenReturn(1);
        List<EquityPoint> points=new ArrayList<>();for(int i=0;i<1201;i++)points.add(new EquityPoint(Instant.ofEpochMilli(i),BigDecimal.ONE,BigDecimal.ZERO,false));
        BacktestMetrics m=new BacktestMetrics(0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        BacktestResult result=new BacktestResult("EMA_CROSS_LONG_ONLY","1.0.0",Map.of("fastPeriod",12,"slowPeriod",26),"BTCUSDT",com.aiprovider.quant.market.model.KlineInterval.M1,1201,Instant.EPOCH,Instant.ofEpochMilli(1201*60000L),"TEST",BigDecimal.ZERO,BigDecimal.ONE,m,List.of(),points,List.of("中文 warning"));
        new BacktestPersistenceService(runs,trades,equity,new ObjectMapper(),p).persistCompleted("r",result);
        verify(equity,times(3)).insertBatch(any()); verify(runs).complete(any());
    }
}
