package com.aiprovider.controller.quant;

import com.aiprovider.common.Result;
import com.aiprovider.controller.quant.dto.BacktestDtos;
import com.aiprovider.service.quant.BacktestRunService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import java.util.List;

class QuantBacktestControllerContractTest {
    @Test void exposesTypedResultAndBeanValidation(){
        BacktestRunService service=mock(BacktestRunService.class);when(service.strategies()).thenReturn(List.of(new BacktestDtos.Strategy("EMA_CROSS_LONG_ONLY","EMA","1.0.0","x",27,List.of(),List.of("USDM_PERPETUAL"),List.of("USDM_PERPETUAL_LONG_ONLY_1X_V1"),List.of("LONG_ONLY"),List.of("OHLCV"))));
        MockMvc mvc=standaloneSetup(new QuantBacktestController(service,new com.aiprovider.quant.execution.ExecutionProfileRegistry())).build();
        try {
            mvc.perform(get("/api/quant/backtests/strategies"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].code").value("EMA_CROSS_LONG_ONLY"))
                    .andExpect(jsonPath("$.data[0].supportedMarketTypes[0]").value("USDM_PERPETUAL"))
                    .andExpect(jsonPath("$.data[0].supportedExecutionProfileCodes[0]").value("USDM_PERPETUAL_LONG_ONLY_1X_V1"))
                    .andExpect(jsonPath("$.data[0].supportedDirectionModes[0]").value("LONG_ONLY"))
                    .andExpect(jsonPath("$.data[0].requiredMarketFeatures[0]").value("OHLCV"));
            mvc.perform(get("/api/quant/backtests/execution-profiles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].code").value("USDM_PERPETUAL_LONG_ONLY_1X_V1"))
                    .andExpect(jsonPath("$.data[0].marketType").value("USDM_PERPETUAL"))
                    .andExpect(jsonPath("$.data[0].directionMode").value("LONG_ONLY"))
                    .andExpect(jsonPath("$.data[0].orderSizingMode").value("BASE_QUANTITY"))
                    .andExpect(jsonPath("$.data[0].positionSide").value("LONG"))
                    .andExpect(jsonPath("$.data[0].entryOrderSide").value("BUY"))
                    .andExpect(jsonPath("$.data[0].exitOrderSide").value("SELL"))
                    .andExpect(jsonPath("$.data[0].leverage").value(1))
                    .andExpect(jsonPath("$.data[0].fillModel").value("TA4J_TRADE_ON_NEXT_OPEN"))
                    .andExpect(jsonPath("$.data[0].transactionCostModel").value("LINEAR_FEE_RATE"))
                    .andExpect(jsonPath("$.data[0].holdingCostModel").value("ZERO"))
                    .andExpect(jsonPath("$.data[0].fundingCostModel").value("ZERO_NOT_MODELED"))
                    .andExpect(jsonPath("$.data[0].liquidationModel").value("NONE_NOT_MODELED"))
                    .andExpect(jsonPath("$.data[0].marginModel").value("NONE_NOT_MODELED"))
                    .andExpect(jsonPath("$.data[0].requiredMarketFeatures[0]").value("OHLCV"))
                    .andExpect(jsonPath("$.data[0].limitations.length()").value(5));
            mvc.perform(post("/api/quant/backtests/runs").contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        } catch(Exception e){throw new AssertionError(e);}
    }
}
