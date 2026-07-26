package com.aiprovider.service.quant;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BacktestExperimentContractTest {
    @Test void creationIsTheOnlyTransactionalExperimentWriteBoundary() throws Exception {
        assertNotNull(BacktestExperimentService.class.getMethod("create", com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest.class).getAnnotation(Transactional.class));
        String dispatcher=Files.readString(Path.of("src/main/java/com/aiprovider/service/quant/BacktestExperimentDispatcher.java"));
        assertTrue(dispatcher.contains("createWithRunId"));
        assertTrue(dispatcher.contains("getMaxActiveCandidatesPerExperiment"));
        assertTrue(Files.readString(Path.of("src/main/java/com/aiprovider/mapper/BacktestExperimentCandidateMapper.java")).contains("claimNextPending"));
    }
}
