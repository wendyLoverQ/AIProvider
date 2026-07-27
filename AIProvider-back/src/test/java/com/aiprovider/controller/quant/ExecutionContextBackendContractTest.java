package com.aiprovider.controller.quant;

import static org.junit.jupiter.api.Assertions.*;

import com.aiprovider.controller.quant.dto.BacktestCreateRequest;
import com.aiprovider.controller.quant.dto.BacktestExperimentCreateRequest;
import com.aiprovider.controller.quant.dto.WalkForwardStudyCreateRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.Test;

class ExecutionContextBackendContractTest {
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allThreeCreateRequestsRequireTheExecutionContext() {
        assertRequired(new BacktestCreateRequest());
        assertRequired(new BacktestExperimentCreateRequest());
        assertRequired(new WalkForwardStudyCreateRequest());
    }

    @Test
    void allThreeChainsReuseCompatibilityAndDispatchersPropagateStoredFields() throws Exception {
        String run =
                Files.readString(
                        Path.of("src/main/java/com/aiprovider/service/quant/BacktestRunService.java"));
        String experiment =
                Files.readString(
                        Path.of(
                                "src/main/java/com/aiprovider/service/quant/BacktestExperimentService.java"));
        String study =
                Files.readString(
                        Path.of(
                                "src/main/java/com/aiprovider/service/quant/WalkForwardStudyCreationService.java"));
        assertTrue(run.contains("compatibility.validate("));
        assertTrue(experiment.contains("compatibility.validate("));
        assertTrue(study.contains("compatibility.validate("));

        String experimentDispatcher =
                Files.readString(
                        Path.of(
                                "src/main/java/com/aiprovider/service/quant/BacktestExperimentDispatcher.java"));
        String studyDispatcher =
                Files.readString(
                        Path.of(
                                "src/main/java/com/aiprovider/service/quant/WalkForwardStudyDispatcher.java"));
        for (String setter :
                new String[] {
                    "setExecutionProfileCode", "setDirectionMode", "setOrderSizingMode"
                }) {
            assertTrue(experimentDispatcher.contains(setter));
            assertTrue(studyDispatcher.contains(setter));
        }
    }

    private void assertRequired(Object request) {
        var paths =
                validator.validate(request).stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .toList();
        assertTrue(paths.contains("executionProfileCode"));
        assertTrue(paths.contains("directionMode"));
        assertTrue(paths.contains("orderSizingMode"));
    }
}
