package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import com.aiprovider.model.dto.LocalGeneratedImagePathsDTO;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.repository.LocalGeneratedImageRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalGeneratedImageServiceTest {
    @Test void preservesMissingPromptsAsNullInsteadOfWritingDisplayPlaceholders() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        LocalGeneratedImageService service = new LocalGeneratedImageService(repository);
        LocalGeneratedImageItemDTO item = new LocalGeneratedImageItemDTO();
        item.setPromptId("prompt-without-text"); item.setImagePath("aimaid/no-prompt.png");
        item.setPrompt("   "); item.setNegativePrompt("");
        LocalGeneratedImageBatchDTO batch = new LocalGeneratedImageBatchDTO();
        batch.setPlatform("Windows"); batch.setItems(Collections.singletonList(item));

        assertThat(service.saveBatch(batch)).isEqualTo(1);
        assertThat(item.getPrompt()).isNull();
        assertThat(item.getNegativePrompt()).isNull();
        verify(repository).upsertBatch(eq("Windows"), argThat(rows -> rows.size() == 1 && rows.get(0).get("item") == item));
    }

    @Test void pagesTheBackendLocalImageQueueAndClampsADeletedLastPage() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        when(repository.count("Windows", "ACTIVE")).thenReturn(101L);
        when(repository.findPage("Windows", "ACTIVE", 100, 100)).thenReturn(Collections.singletonList(Collections.singletonMap("id", 101L)));

        GalleryRecordPageVO page = new LocalGeneratedImageService(repository).page("windows", 9, 100, "active");

        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getPages()).isEqualTo(2);
        assertThat(page.getItems()).singleElement().satisfies(item -> assertThat(item.get("id")).isEqualTo(101L));
    }

    @Test void changesQueueStateByStableNormalizedPathHash() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        when(repository.trash(eq("Windows"), anyList())).thenReturn(1);
        LocalGeneratedImagePathsDTO dto = new LocalGeneratedImagePathsDTO();
        dto.setPlatform("Windows"); dto.setPaths(Collections.singletonList("AIMAID\\Result.PNG"));

        assertThat(new LocalGeneratedImageService(repository).trash(dto)).isEqualTo(1);

        verify(repository).trash(eq("Windows"), argThat(items -> items.size() == 1 && items.get(0).matches("[0-9a-f]{64}")));
    }
}
