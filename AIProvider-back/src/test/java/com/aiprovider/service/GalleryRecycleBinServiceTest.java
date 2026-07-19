package com.aiprovider.service;

import com.aiprovider.repository.GalleryRecycleBinRepository;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GalleryRecycleBinServiceTest {
    @Test void exposesOneBackendPaginationQueueForLocalAndAssetTrash() {
        GalleryRecycleBinRepository repository = mock(GalleryRecycleBinRepository.class);
        when(repository.count("Windows")).thenReturn(205L);
        when(repository.findPage("Windows", 100, 200)).thenReturn(Arrays.asList(
                Collections.singletonMap("source", "local"),
                Collections.singletonMap("source", "asset")
        ));

        GalleryRecordPageVO page = new GalleryRecycleBinService(repository).page("Windows", 3, 100);

        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPages()).isEqualTo(3);
        assertThat(page.getItems()).extracting(item -> item.get("source")).containsExactly("local", "asset");
    }
}
