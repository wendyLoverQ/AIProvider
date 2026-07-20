package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageIdsDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.model.vo.LocalGeneratedImageBatchResultVO;
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
        item.setMainModel("  flux\\dev.safetensors  ");
        LocalGeneratedImageBatchDTO batch = new LocalGeneratedImageBatchDTO();
        batch.setPlatform("Windows"); batch.setItems(Collections.singletonList(item));

        when(repository.findByPathHashes(eq("Windows"), anyList())).thenReturn(Collections.singletonList(Map.of("id", 7L, "imagePath", "aimaid/no-prompt.png")));
        LocalGeneratedImageBatchResultVO result = service.saveBatch(batch);
        assertThat(result.getSaved()).isEqualTo(1);
        assertThat(result.getItems()).extracting(row -> row.get("id")).containsExactly(7L);
        assertThat(item.getPrompt()).isNull();
        assertThat(item.getNegativePrompt()).isNull();
        assertThat(item.getMainModel()).isEqualTo("flux\\dev.safetensors");
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

    @Test void changesQueueStateByStableDatabaseIds() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        when(repository.trash(eq("Windows"), anyList())).thenReturn(1);
        LocalGeneratedImageIdsDTO dto = new LocalGeneratedImageIdsDTO();
        dto.setPlatform("Windows"); dto.setIds(Arrays.asList(42L, 42L));

        assertThat(new LocalGeneratedImageService(repository).trash(dto)).isEqualTo(1);

        verify(repository).trash("Windows", Collections.singletonList(42L));
    }

    @Test void rejectsAQueueMutationWhenAnyRequestedIdWasNotChanged() {
        LocalGeneratedImageRepository repository = mock(LocalGeneratedImageRepository.class);
        when(repository.trash(eq("Windows"), anyList())).thenReturn(1);
        LocalGeneratedImageIdsDTO dto = new LocalGeneratedImageIdsDTO();
        dto.setPlatform("Windows"); dto.setIds(Arrays.asList(42L, 43L));

        assertThatThrownBy(() -> new LocalGeneratedImageService(repository).trash(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("请求 2，实际 1");
    }
}
