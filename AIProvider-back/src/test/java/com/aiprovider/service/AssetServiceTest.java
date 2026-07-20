package com.aiprovider.service;

import com.aiprovider.model.dto.AssetBatchDTO;
import com.aiprovider.model.dto.AssetDeleteDTO;
import com.aiprovider.model.dto.AssetItemDTO;
import com.aiprovider.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetServiceTest {
    @Test void infersBroadAndSpecificMediaTypesForExistingImageAssets() {
        AssetRepository repository = mock(AssetRepository.class);
        when(repository.findByPathHashes(anyString(), anyList())).thenReturn(Collections.emptyList());
        AssetItemDTO item = new AssetItemDTO(); item.setLocalPath("C:\\assets\\result.png"); item.setLocalUrl("http://127.0.0.1/result.png"); item.setFileName("result.png"); item.setMainModel("  flux\\dev.safetensors  ");
        AssetBatchDTO dto = new AssetBatchDTO(); dto.setPlatform("Windows"); dto.setItems(Collections.singletonList(item));

        new AssetService(repository).saveBatch(dto);

        ArgumentCaptor<List<Map<String,Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).upsertBatch(eq("Windows"), captor.capture());
        AssetItemDTO saved = (AssetItemDTO) captor.getValue().get(0).get("item");
        assertThat(saved.getAssetType()).isEqualTo("image");
        assertThat(saved.getMimeType()).isEqualTo("image/png");
        assertThat(saved.getMainModel()).isEqualTo("flux\\dev.safetensors");
    }

    @Test void returnsOnlyTheRepositoryImagePromptPoolWithWeights() {
        AssetRepository repository = mock(AssetRepository.class);
        Map<String,Object> row = new HashMap<>(); row.put("prompt", "masterpiece"); row.put("negativePrompt", "bad anatomy"); row.put("weight", 7L);
        when(repository.findImagePromptPool("Windows")).thenReturn(Collections.singletonList(row));
        assertThat(new AssetService(repository).imagePromptPool("Windows")).singleElement().satisfies(item -> {
            assertThat(item.getPrompt()).isEqualTo("masterpiece"); assertThat(item.getWeight()).isEqualTo(7);
        });
    }

    @Test void movesAssetsToTrashAndRestoresTheirPreviousStatus() {
        AssetRepository repository = mock(AssetRepository.class);
        when(repository.trashByIds(eq("Windows"), anyList())).thenReturn(2);
        when(repository.restoreByIds(eq("Windows"), anyList())).thenReturn(2);
        AssetDeleteDTO dto = new AssetDeleteDTO(); dto.setPlatform("Windows"); dto.setIds(Arrays.asList(3L, 4L));
        AssetService service = new AssetService(repository);

        assertThat(service.trash(dto)).isEqualTo(2);
        assertThat(service.restore(dto)).isEqualTo(2);
        verify(repository).trashByIds("Windows", Arrays.asList(3L, 4L));
        verify(repository).restoreByIds("Windows", Arrays.asList(3L, 4L));
    }
}
