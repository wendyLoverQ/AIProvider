package com.aiprovider.controller.quant;

import com.aiprovider.controller.quant.dto.BacktestExperimentDtos;
import com.aiprovider.service.quant.BacktestExperimentCreationService;
import com.aiprovider.service.quant.BacktestExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class QuantBacktestExperimentControllerContractTest {
    @Test void exposesTheFourFrozenExperimentEndpoints() throws Exception {
        BacktestExperimentCreationService creation=mock(BacktestExperimentCreationService.class); BacktestExperimentService service=mock(BacktestExperimentService.class);
        when(creation.create(any())).thenReturn(new BacktestExperimentDtos.CreateResponse("e",2,4));
        MockMvc mvc=standaloneSetup(new QuantBacktestExperimentController(creation,service)).build();
        mvc.perform(post("/api/quant/backtests/experiments").contentType("application/json").content("{\"datasetId\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.experimentId").value("e"));
        mvc.perform(get("/api/quant/backtests/experiments")).andExpect(status().isOk());
        mvc.perform(get("/api/quant/backtests/experiments/e")).andExpect(status().isOk());
        mvc.perform(get("/api/quant/backtests/experiments/e/candidates")).andExpect(status().isOk());
        verify(creation).create(any());
    }
}
