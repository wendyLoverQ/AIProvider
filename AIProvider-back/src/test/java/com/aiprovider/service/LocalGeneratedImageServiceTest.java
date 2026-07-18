package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import com.aiprovider.repository.LocalGeneratedImageRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalGeneratedImageServiceTest {
    @Test
    void preservesMissingPromptsAsNullInsteadOfWritingDisplayPlaceholders() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        when(repository.upsert(eq("Windows"), anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        LocalGeneratedImageService service = new LocalGeneratedImageService(repository);
        LocalGeneratedImageItemDTO item = new LocalGeneratedImageItemDTO();
        item.setPromptId("prompt-without-text");
        item.setImagePath("aimaid/no-prompt.png");
        item.setPrompt("   ");
        item.setNegativePrompt("");
        LocalGeneratedImageBatchDTO batch = new LocalGeneratedImageBatchDTO();
        batch.setPlatform("Windows");
        batch.setItems(Collections.singletonList(item));

        assertThat(service.saveBatch(batch)).isEqualTo(1);
        assertThat(item.getPrompt()).isNull();
        assertThat(item.getNegativePrompt()).isNull();
        verify(repository).upsert(eq("Windows"), anyString(), eq(item));
    }
}
