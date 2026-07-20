package com.aiprovider.service;

import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import com.aiprovider.repository.ComfyTaskRepository;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ComfyTaskServiceTest {
    @Test
    void savesOneTaskListWithOneBatchRepositoryCall() {
        ComfyTaskRepository repository = mock(ComfyTaskRepository.class);
        ComfyTaskService service = new ComfyTaskService(repository);
        ComfyTaskRecordDTO first = task("p1");
        ComfyTaskRecordDTO second = task("p2");

        service.saveBatch(Arrays.asList(first, second));

        verify(repository, times(1)).saveBatch(Arrays.asList(first, second));
        verify(repository, never()).save(any());
        assertEquals("QUEUED", first.getStatus());
        assertEquals("QUEUED", second.getStatus());
    }

    @Test
    void checksAllHashesWithOneBatchRepositoryCall() {
        ComfyTaskRepository repository = mock(ComfyTaskRepository.class);
        ComfyTaskService service = new ComfyTaskService(repository);
        String first = "a".repeat(64);
        String second = "b".repeat(64);
        when(repository.findDuplicateHashes("wf", Arrays.asList(first, second))).thenReturn(Collections.singletonList(second));

        List<String> duplicates = service.duplicateHashes("wf", Arrays.asList(first, second));

        assertEquals(Collections.singletonList(second), duplicates);
        verify(repository, times(1)).findDuplicateHashes("wf", Arrays.asList(first, second));
        verify(repository, never()).findDuplicate(anyString(), anyString());
    }

    private static ComfyTaskRecordDTO task(String promptId) {
        ComfyTaskRecordDTO dto = new ComfyTaskRecordDTO();
        dto.setPromptId(promptId);
        dto.setWorkflowId("wf");
        return dto;
    }
}
