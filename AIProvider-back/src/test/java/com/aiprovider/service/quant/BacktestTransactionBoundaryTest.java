package com.aiprovider.service.quant;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

class BacktestTransactionBoundaryTest {
    @Test void completionIsTransactionalAndFailureUsesRequiresNew() throws Exception{
        assertNotNull(BacktestPersistenceService.class.getMethod("persistCompleted",String.class,com.aiprovider.quant.backtest.BacktestResult.class).getAnnotation(Transactional.class));
        Transactional tx=BacktestFailureService.class.getMethod("markFailed",String.class,String.class,String.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW,tx.propagation());
    }
}
