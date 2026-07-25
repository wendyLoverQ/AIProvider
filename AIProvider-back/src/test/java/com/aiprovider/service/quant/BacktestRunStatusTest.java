package com.aiprovider.service.quant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BacktestRunStatusTest {
    @Test void terminalStatesAreTerminal() {
        assertTrue(BacktestRunStatus.COMPLETED.terminal());
        assertTrue(BacktestRunStatus.FAILED.terminal());
        assertFalse(BacktestRunStatus.QUEUED.terminal());
        assertFalse(BacktestRunStatus.PERSISTING.terminal());
    }
}
