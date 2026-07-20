package com.aiprovider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class MaidAvatarService {

    private final Path avatarDirectory;

    public MaidAvatarService(@Value("${maid.avatar-directory:/opt/aiprovider/characters}") String avatarDirectory) {
        this.avatarDirectory = Paths.get(avatarDirectory).toAbsolutePath().normalize();
    }

    public Resource find(String roleId) throws IOException {
        if (roleId == null || !roleId.matches("[A-Za-z0-9_-]{1,96}") || !Files.isDirectory(avatarDirectory)) {
            return null;
        }
        String expectedStem = roleId.toLowerCase(Locale.ROOT);
        try (Stream<Path> files = Files.walk(avatarDirectory, 3)) {
            Optional<Path> match = files
                    .filter(Files::isRegularFile)
                    .filter(path -> isImage(path) && stem(path).equalsIgnoreCase(expectedStem))
                    .findFirst();
            return match.isPresent() ? new FileSystemResource(match.get().toFile()) : null;
        }
    }

    public Resource save(String roleId, MultipartFile file) throws IOException {
        if (roleId == null || !roleId.matches("[A-Za-z0-9_-]{1,96}"))
            throw new IllegalArgumentException("无效的角色 ID");
        if (file == null || file.isEmpty() || file.getSize() > 8L * 1024 * 1024)
            throw new IllegalArgumentException("角色头像图片为空或超过 8MB");
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        String extension = original.endsWith(".jpeg") ? ".jpeg"
                : original.endsWith(".jpg") ? ".jpg"
                : original.endsWith(".webp") ? ".webp"
                : original.endsWith(".png") ? ".png" : "";
        if (extension.isEmpty() || !contentType.startsWith("image/"))
            throw new IllegalArgumentException("角色头像必须是 PNG、JPG、JPEG 或 WEBP 图片");
        Files.createDirectories(avatarDirectory);
        Path target = avatarDirectory.resolve(roleId.toLowerCase(Locale.ROOT) + extension).normalize();
        if (!target.getParent().equals(avatarDirectory)) throw new IllegalArgumentException("无效的角色头像路径");
        Path temporary = Files.createTempFile(avatarDirectory, roleId + "-", ".upload");
        try {
            file.transferTo(temporary);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            for (String other : new String[]{".png", ".jpg", ".jpeg", ".webp"})
                if (!other.equals(extension)) Files.deleteIfExists(avatarDirectory.resolve(roleId.toLowerCase(Locale.ROOT) + other));
            return new FileSystemResource(target.toFile());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        int extension = name.lastIndexOf('.');
        return extension > 0 ? name.substring(0, extension) : name;
    }
}
