package com.aiprovider.controller.quant;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class QuantBacktestControllerContractTest {
    @Test void usesFrozenPrefixAndNoFrontendChangesAreRequired() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/aiprovider/controller/quant/QuantBacktestController.java"));
        assertTrue(source.contains("/api/quant/backtests"));
        assertTrue(source.contains("/runs/non-terminal"));
        assertTrue(source.contains("/runs/{runId}/equity"));
    }
}
