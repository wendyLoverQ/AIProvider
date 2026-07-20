package com.aiprovider.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaidAvatarServiceTest {
    @TempDir Path directory;

    @Test void storesTheDatabaseBackedRoleAvatarUnderItsStableRoleId() throws Exception {
        MaidAvatarService service = new MaidAvatarService(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "characters/yae_miko.png", "image/png", new byte[]{1, 2, 3});

        Resource stored = service.save("yae_miko", file);

        assertThat(stored.exists()).isTrue();
        assertThat(stored.getFilename()).isEqualTo("yae_miko.png");
        assertThat(service.find("yae_miko").getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test void rejectsNonImageAvatarUploads() {
        MaidAvatarService service = new MaidAvatarService(directory.toString());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> service.save("yae_miko", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("图片");
    }
}
