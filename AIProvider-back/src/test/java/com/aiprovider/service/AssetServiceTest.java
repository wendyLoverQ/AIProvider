package com.aiprovider.service;

import com.aiprovider.model.dto.AssetBatchDTO;
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
        when(repository.upsert(anyString(), anyString(), any())).thenReturn(1);
        when(repository.findByPathHashes(anyString(), anyList())).thenReturn(Collections.emptyList());
        AssetItemDTO item = new AssetItemDTO(); item.setLocalPath("C:\\assets\\result.png"); item.setLocalUrl("http://127.0.0.1/result.png"); item.setFileName("result.png");
        AssetBatchDTO dto = new AssetBatchDTO(); dto.setPlatform("Windows"); dto.setItems(Collections.singletonList(item));

        new AssetService(repository).saveBatch(dto);

        ArgumentCaptor<AssetItemDTO> captor = ArgumentCaptor.forClass(AssetItemDTO.class);
        verify(repository).upsert(eq("Windows"), anyString(), captor.capture());
        assertThat(captor.getValue().getAssetType()).isEqualTo("image");
        assertThat(captor.getValue().getMimeType()).isEqualTo("image/png");
    }

    @Test void returnsOnlyTheRepositoryImagePromptPoolWithWeights() {
        AssetRepository repository = mock(AssetRepository.class);
        Map<String,Object> row = new HashMap<>(); row.put("prompt", "masterpiece"); row.put("negativePrompt", "bad anatomy"); row.put("weight", 7L);
        when(repository.findImagePromptPool("Windows")).thenReturn(Collections.singletonList(row));
        assertThat(new AssetService(repository).imagePromptPool("Windows")).singleElement().satisfies(item -> {
            assertThat(item.getPrompt()).isEqualTo("masterpiece"); assertThat(item.getWeight()).isEqualTo(7);
        });
    }
}
