package com.aiprovider.repository;

import com.aiprovider.mapper.ComfyPresetMapper;
import com.aiprovider.mapper.ComfyWorkflowMapper;
import com.aiprovider.mapper.PromptCatalogMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComfyRepositoryTest {
    @Test void presetRepositoryCoversReadWriteDelete() {
        ComfyPresetMapper mapper = mock(ComfyPresetMapper.class); ComfyPresetRepository repository = new ComfyPresetRepository(mapper);
        List<Map<String, Object>> rows = Collections.singletonList(Collections.singletonMap("id", 1)); when(mapper.findAll()).thenReturn(rows);
        assertThat(repository.findAll()).isSameAs(rows);
        ComfyPresetMapper.PresetRecord insert = new ComfyPresetMapper.PresetRecord(); insert.setId(4L);
        assertThat(repository.insert(insert)).isEqualTo(4L); verify(mapper).insert(insert);
        when(mapper.update(insert)).thenReturn(1); assertThat(repository.update(insert)).isTrue();
        repository.clearDefault(); verify(mapper).clearDefault(); when(mapper.setDefault(4)).thenReturn(1); assertThat(repository.setDefault(4)).isTrue();
        when(mapper.delete(4)).thenReturn(1); when(mapper.delete(5)).thenReturn(0);
        assertThat(repository.delete(4)).isTrue(); assertThat(repository.delete(5)).isFalse();
    }

    @Test void promptCatalogRepositoryCoversCatalogReads() {
        PromptCatalogMapper mapper = mock(PromptCatalogMapper.class); PromptCatalogRepository repository = new PromptCatalogRepository(mapper);
        List<Map<String, Object>> rows = Collections.singletonList(Collections.singletonMap("id", "solo")); when(mapper.findEnabledOptions()).thenReturn(rows);
        when(mapper.findGeneralNegativePrompt()).thenReturn("negative");
        assertThat(repository.findEnabledOptions()).isSameAs(rows); assertThat(repository.findGeneralNegativePrompt()).isEqualTo("negative");
    }

    @Test void workflowRepositoryCoversReadAndExistence() {
        ComfyWorkflowMapper mapper = mock(ComfyWorkflowMapper.class); ComfyWorkflowRepository repository = new ComfyWorkflowRepository(mapper);
        List<Map<String, Object>> rows = Collections.singletonList(Collections.singletonMap("id", "futa01")); when(mapper.findActive()).thenReturn(rows);
        when(mapper.countActive("yes")).thenReturn(1); when(mapper.countActive("no")).thenReturn(0);
        assertThat(repository.findActive()).isSameAs(rows); assertThat(repository.existsActive("yes")).isTrue(); assertThat(repository.existsActive("no")).isFalse();
    }
}
