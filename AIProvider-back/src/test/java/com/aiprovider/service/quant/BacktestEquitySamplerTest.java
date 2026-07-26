package com.aiprovider.service.quant;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BacktestEquitySamplerTest {
    @Test void smallSeriesIsReturnedInFull(){assertEquals(List.of(0,1,2),BacktestEquitySampler.indices(3,100));}
    @Test void largeSeriesIsDeterministicAndKeepsBothBoundaries(){List<Integer> a=BacktestEquitySampler.indices(50000,1200);assertEquals(a,BacktestEquitySampler.indices(50000,1200));assertEquals(1200,a.size());assertEquals(0,a.get(0));assertEquals(49999,a.get(a.size()-1));for(int i=1;i<a.size();i++)assertTrue(a.get(i)>a.get(i-1));}
}
