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
        BacktestRunService service=mock(BacktestRunService.class);when(service.strategies()).thenReturn(List.of(new BacktestDtos.Strategy("EMA_CROSS_LONG_ONLY","EMA","1.0.0","x",27,List.of())));
        MockMvc mvc=standaloneSetup(new QuantBacktestController(service)).build();
        try { mvc.perform(get("/api/quant/backtests/strategies")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].code").value("EMA_CROSS_LONG_ONLY")); mvc.perform(post("/api/quant/backtests/runs").contentType("application/json").content("{}" )).andExpect(status().isBadRequest()); } catch(Exception e){throw new AssertionError(e);}
    }
}
