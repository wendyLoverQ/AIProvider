package com.aiprovider.service;

import com.aiprovider.repository.MonitorRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MonitorRetentionServiceTest {
    @Test
    void stopsTheBatchWhenHttpMetricAffectedRowsDoNotMatch() {
        MonitorRepository repository=mock(MonitorRepository.class);
        when(repository.countExpiredHttpRequests(30)).thenReturn(5);
        when(repository.deleteExpiredHttpRequests(30)).thenReturn(4);

        assertThrows(IllegalStateException.class,() -> new MonitorRetentionService(repository,30).cleanup());

        verify(repository,never()).deleteExpired(anyInt());
    }

    @Test
    void verifiesRequestedAndAffectedRowsForBothRetentionBatches() {
        MonitorRepository repository=mock(MonitorRepository.class);
        when(repository.countExpiredHttpRequests(30)).thenReturn(5);when(repository.deleteExpiredHttpRequests(30)).thenReturn(5);
        when(repository.countExpired(30)).thenReturn(3);when(repository.deleteExpired(30)).thenReturn(3);

        new MonitorRetentionService(repository,30).cleanup();

        verify(repository).countExpiredHttpRequests(30);verify(repository).deleteExpiredHttpRequests(30);
        verify(repository).countExpired(30);verify(repository).deleteExpired(30);
    }
}
