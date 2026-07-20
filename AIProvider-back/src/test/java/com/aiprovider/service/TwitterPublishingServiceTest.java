package com.aiprovider.service;

import com.aiprovider.mapper.TwitterMapper;
import com.aiprovider.model.dto.TwitterPostCreateDTO;
import com.aiprovider.repository.AssetRepository;
import com.aiprovider.repository.TwitterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwitterPublishingServiceTest {
    @Test
    void storesScheduledGalleryImageAndKeepsAssetId(@TempDir Path storage) {
        TwitterRepository repository = mock(TwitterRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        Map<String, Object> account = new HashMap<>();
        account.put("sessionStatus", "CONNECTED");
        when(repository.findAccount(2L)).thenReturn(account);
        when(repository.insertPost(any(TwitterMapper.PostInsert.class))).thenReturn(41L);
        when(assets.findExistingIds(Collections.singletonList(12L))).thenReturn(Collections.singletonList(12L));
        TwitterPublishingService service = new TwitterPublishingService(
                repository, assets, mock(TwitterWebPublisher.class), mock(TwitterSessionCipher.class), mock(PlatformAccountCredentialService.class),
                new SyncTaskExecutor(), storage.toString(), "client");

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile image = new MockMultipartFile("images", "result.png", "image/png", png);
        TwitterPostCreateDTO dto = new TwitterPostCreateDTO();
        dto.setAccountId(2L);
        dto.setContent("");
        dto.setDelayMinutes(5);
        dto.setImages(Collections.singletonList(image));
        dto.setAssetIds(Collections.singletonList(12L));

        assertEquals(41L, service.createPost(dto));
        verify(repository).insertMediaBatch(argThat(media -> media.size() == 1
                && media.get(0).getAssetId() == 12L && media.get(0).getStoragePath() != null
                && media.get(0).getLocalPath() == null));
        verify(repository).insertPost(argThat(post ->
                post.getContent().isEmpty() && "GALLERY".equals(post.getSource())
                        && post.getScheduledAt() != null));
        assertTrue(dto.getContent().isEmpty());
    }

    @Test
    void cancelsPendingOrFailedTask(@TempDir Path storage) {
        TwitterRepository repository = mock(TwitterRepository.class);
        when(repository.findPost(9L)).thenReturn(Collections.singletonMap("id", 9L));
        when(repository.cancelPost(9L)).thenReturn(true);
        TwitterPublishingService service = new TwitterPublishingService(
                repository, mock(AssetRepository.class), mock(TwitterWebPublisher.class), mock(TwitterSessionCipher.class), mock(PlatformAccountCredentialService.class),
                new SyncTaskExecutor(), storage.toString(), "client");

        service.cancel(9L);

        verify(repository).cancelPost(9L);
    }
}
