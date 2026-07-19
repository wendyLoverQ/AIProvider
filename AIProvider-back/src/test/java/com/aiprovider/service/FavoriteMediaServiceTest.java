package com.aiprovider.service;

import com.aiprovider.mapper.FavoriteMediaMapper;
import com.aiprovider.model.vo.FavoriteMediaVO;
import com.aiprovider.repository.AssetRepository;
import com.aiprovider.repository.FavoriteMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FavoriteMediaServiceTest {
    @TempDir Path directory;
    private static final byte[] PNG = new byte[] {(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,1,2,3};

    @Test void storesARealServerFileAndReturnsAStableContentUrl() throws Exception {
        FavoriteMediaRepository repository = mock(FavoriteMediaRepository.class);
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findById(9)).thenReturn(Collections.singletonMap("id", 9L));
        when(repository.findBySha256(anyString())).thenReturn(null);
        when(repository.insert(any())).thenAnswer(invocation -> { invocation.<FavoriteMediaMapper.Row>getArgument(0).setId(31L); return 1; });
        when(repository.findById(31)).thenReturn(row(31L, "ab/file.png"));
        FavoriteMediaService service = new FavoriteMediaService(repository, assets, directory.toString());

        FavoriteMediaVO saved = service.upload(new MockMultipartFile("file", "海岸.png", "image/png", PNG), 9L, "海岸", 1920, 1080, "ocean", "Windows");

        assertThat(saved.getId()).isEqualTo(31L);
        assertThat(saved.getContentUrl()).isEqualTo("/api/favorites/31/content");
        ArgumentCaptor<FavoriteMediaMapper.Row> captor = ArgumentCaptor.forClass(FavoriteMediaMapper.Row.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getAssetId()).isEqualTo(9L);
        assertThat(captor.getValue().getStoragePath()).matches("[0-9a-f]{2}/[0-9a-f]{64}\\.png");
        assertThat(Files.walk(directory).filter(Files::isRegularFile)).hasSize(1);
    }

    @Test void rejectsFilesWhoseBytesAreNotASupportedImage() {
        FavoriteMediaService service = new FavoriteMediaService(mock(FavoriteMediaRepository.class), mock(AssetRepository.class), directory.toString());
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "fake.png", "image/png", "not image".getBytes()), null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("当前仅支持真实的 PNG、JPEG、WEBP 或 GIF 图片");
    }

    @Test void removesBothDatabaseRecordAndStoredFile() throws Exception {
        FavoriteMediaRepository repository = mock(FavoriteMediaRepository.class);
        Path stored = directory.resolve("ab/file.png"); Files.createDirectories(stored.getParent()); Files.write(stored, PNG);
        when(repository.findById(31)).thenReturn(row(31L, "ab/file.png"));
        when(repository.deleteByIds(Collections.singletonList(31L))).thenReturn(1);
        FavoriteMediaService service = new FavoriteMediaService(repository, mock(AssetRepository.class), directory.toString());
        assertThat(service.delete(Collections.singletonList(31L))).isEqualTo(1);
        assertThat(stored).doesNotExist();
    }

    private static Map<String,Object> row(long id, String storagePath) {
        Map<String,Object> row = new HashMap<>(); row.put("id", id); row.put("storagePath", storagePath);
        row.put("originalFileName", "海岸.png"); row.put("title", "海岸"); row.put("mediaType", "image"); row.put("contentType", "image/png"); row.put("fileSize", 11L);
        row.put("width", 1920); row.put("height", 1080); row.put("prompt", "ocean"); row.put("sourcePlatform", "Windows"); row.put("createdAt", LocalDateTime.now());
        return row;
    }
}
