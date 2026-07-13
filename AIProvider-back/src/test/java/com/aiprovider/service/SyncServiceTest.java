package com.aiprovider.service;

import com.aiprovider.model.dto.SyncBatchDTO;
import com.aiprovider.repository.SyncRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SyncServiceTest {
    private final SyncRepository repository = mock(SyncRepository.class);
    private final SyncService service = new SyncService(repository);

    @Test void mapsLegacyMaidSourceNamesToPrefixedDatabaseTables() {
        SyncBatchDTO.BusinessRecord record = new SyncBatchDTO.BusinessRecord();
        record.setTable("TimerRecords");
        record.setPayload(new ObjectMapper().createObjectNode().put("Id", 7));

        assertThat(service.processBusinessBatch("windows-1", Collections.singletonList(record)).getTables())
            .containsEntry("TimerRecords", 1);
        verify(repository).upsert("maid_TimerRecords", record.getPayload());
        verify(repository).insertSyncRun("windows-1", 1, 1);
    }

    @Test void rejectsProviderOwnedAndUnknownTablesFromTheMaidSyncApi() {
        SyncBatchDTO.BusinessRecord record = new SyncBatchDTO.BusinessRecord();
        record.setTable("TwitterPosts");
        record.setPayload(new ObjectMapper().createObjectNode().put("Id", 8));

        assertThatThrownBy(() -> service.processBusinessBatch("windows-1", Collections.singletonList(record)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不支持的业务表");
    }
}
