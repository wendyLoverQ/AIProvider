package com.aiprovider.service;

import com.aiprovider.model.vo.FileTransferDownload;
import com.aiprovider.model.vo.FileTransferFileVO;
import com.aiprovider.model.vo.FileTransferPreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileTransferServiceTest {
    @TempDir Path directory;

    @Test void uploadsListsDownloadsOverwritesAndDeletesOriginalFileName() throws Exception {
        FileTransferService service = new FileTransferService(directory.resolve("files").toString(), directory.resolve("text.txt").toString());
        service.upload(new MockMultipartFile("file", "设备文件.txt", "text/plain", "first".getBytes(StandardCharsets.UTF_8)));
        service.upload(new MockMultipartFile("file", "设备文件.txt", "text/plain", "second".getBytes(StandardCharsets.UTF_8)));

        List<FileTransferFileVO> files = service.list();
        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file.getFileName()).isEqualTo("设备文件.txt");
            assertThat(file.getFileSize()).isEqualTo(6);
            assertThat(file.getUploadedAt()).isNotNull();
        });
        FileTransferDownload download = service.download("设备文件.txt");
        assertThat(download.getFileSize()).isEqualTo(6);
        assertThat(download.getResource().getInputStream()).hasContent("second");

        service.delete("设备文件.txt");
        assertThat(service.list()).isEmpty();
    }

    @Test void rejectsPathsOutsideConfiguredStorageDirectory() {
        FileTransferService service = new FileTransferService(directory.resolve("files").toString(), directory.resolve("text.txt").toString());
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "../outside.txt", "text/plain", new byte[0])))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("文件名不合法");
        assertThat(Files.exists(directory.getParent().resolve("outside.txt"))).isFalse();
    }

    @Test void streamsImagePreviewAndSelectedFilesAsZip() throws Exception {
        FileTransferService service = new FileTransferService(directory.resolve("files").toString(), directory.resolve("text.txt").toString());
        service.upload(new MockMultipartFile("file", "照片.png", "image/png", "image".getBytes(StandardCharsets.UTF_8)));
        service.upload(new MockMultipartFile("file", "说明.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)));

        FileTransferPreview preview = service.preview("照片.png");
        assertThat(preview.getMediaType()).isEqualTo("image/png");
        assertThat(preview.getResource().getInputStream()).hasContent("image");
        assertThatThrownBy(() -> service.preview("说明.txt"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("该文件不支持图片预览");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.downloadBatch(Arrays.asList("照片.png", "说明.txt", "照片.png")).writeTo(output);
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream value = new ByteArrayOutputStream();
                byte[] buffer = new byte[32];
                int read;
                while ((read = zip.read(buffer)) != -1) value.write(buffer, 0, read);
                entries.put(entry.getName(), new String(value.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsExactlyInAnyOrderEntriesOf(new HashMap<String, String>() {{
            put("照片.png", "image");
            put("说明.txt", "text");
        }});
    }

    @Test void persistsOnlyTheLatestTransferredText() throws Exception {
        FileTransferService service = new FileTransferService(directory.resolve("files").toString(), directory.resolve("transfer.txt").toString());
        assertThat(service.readText()).isEmpty();
        service.saveText("第一台设备\n复制的文本");
        assertThat(service.readText()).isEqualTo("第一台设备\n复制的文本");
        service.saveText("最新文本");
        assertThat(service.readText()).isEqualTo("最新文本");
        assertThat(service.list()).isEmpty();
    }
}
