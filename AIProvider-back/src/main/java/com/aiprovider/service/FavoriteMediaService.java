package com.aiprovider.service;

import com.aiprovider.mapper.FavoriteMediaMapper;
import com.aiprovider.model.vo.FavoriteMediaContent;
import com.aiprovider.model.vo.FavoriteMediaPageVO;
import com.aiprovider.model.vo.FavoriteMediaVO;
import com.aiprovider.repository.AssetRepository;
import com.aiprovider.repository.FavoriteMediaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FavoriteMediaService {
    private static final long MAX_IMAGE_BYTES = 100L * 1024 * 1024;
    private static final String TEMP_PREFIX = ".favorite-upload-";
    private final FavoriteMediaRepository repository;
    private final AssetRepository assetRepository;
    private final Path storageRoot;

    public FavoriteMediaService(FavoriteMediaRepository repository, AssetRepository assetRepository,
                                @Value("${favorites.storage-directory:/opt/aiprovider/favorites}") String storageDirectory) {
        if (storageDirectory == null || storageDirectory.trim().isEmpty()) throw new IllegalArgumentException("我的最爱存储目录不能为空");
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.storageRoot = Paths.get(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public FavoriteMediaVO upload(MultipartFile file, Long assetId, String title, Integer width, Integer height,
                                  String prompt, String sourcePlatform) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的图片");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new IllegalArgumentException("单张图片不能超过 100MB");
        if (assetId != null && assetId > 0 && assetRepository.findById(assetId) == null)
            throw new IllegalArgumentException("来源资产不存在：" + assetId);
        if (width != null && width < 0 || height != null && height < 0) throw new IllegalArgumentException("图片尺寸不能为负数");
        String contentType = detectImageType(file);
        String originalName = safeFileName(file.getOriginalFilename());
        String normalizedTitle = text(title, 255);
        if (normalizedTitle == null) normalizedTitle = stripExtension(originalName);
        String normalizedPlatform = text(sourcePlatform, 20);
        if (normalizedPlatform != null && !Arrays.asList("Windows", "macOS").contains(normalizedPlatform))
            throw new IllegalArgumentException("来源平台仅支持 Windows 或 macOS");

        Files.createDirectories(storageRoot);
        Path temporary = storageRoot.resolve(TEMP_PREFIX + UUID.randomUUID() + ".tmp").normalize();
        ensureWithinRoot(temporary);
        Path target = null;
        boolean targetCreated = false;
        try {
            file.transferTo(temporary);
            String sha256 = sha256(temporary);
            Map<String,Object> existing = repository.findBySha256(sha256);
            if (existing != null) return toVO(existing);
            target = storageRoot.resolve(sha256.substring(0, 2)).resolve(sha256 + extension(contentType)).normalize();
            ensureWithinRoot(target);
            FavoriteMediaMapper.Row row = new FavoriteMediaMapper.Row();
            row.setAssetId(assetId != null && assetId > 0 ? assetId : null);
            row.setStoragePath(storageRoot.relativize(target).toString().replace('\\', '/'));
            row.setOriginalFileName(originalName);
            row.setTitle(normalizedTitle);
            row.setContentType(contentType);
            row.setFileSize(Files.size(temporary));
            row.setSha256(sha256);
            row.setWidth(width); row.setHeight(height);
            row.setPrompt(text(prompt, 16000)); row.setSourcePlatform(normalizedPlatform);
            repository.insert(row);
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) Files.delete(temporary);
            else { moveAtomically(temporary, target); targetCreated = true; }
            Map<String,Object> saved = repository.findById(row.getId());
            if (saved == null) throw new IllegalStateException("我的最爱记录保存后无法读取");
            return toVO(saved);
        } catch (RuntimeException | IOException exception) {
            if (targetCreated && target != null) Files.deleteIfExists(target);
            throw exception;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public FavoriteMediaPageVO page(int page, int pageSize) {
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        List<FavoriteMediaVO> items = new ArrayList<>();
        for (Map<String,Object> row : repository.findPage(pageSize, (page - 1) * pageSize)) items.add(toVO(row));
        return new FavoriteMediaPageVO(items, repository.count(), page, pageSize);
    }

    public FavoriteMediaContent content(long id) throws IOException {
        Map<String,Object> row = required(id);
        Path path = storedPath(text(row.get("storagePath"), 1000));
        if (!Files.isRegularFile(path)) throw new IllegalStateException("我的最爱服务器文件不存在");
        return new FavoriteMediaContent(text(row.get("originalFileName"), 255), text(row.get("contentType"), 100),
                Files.size(path), new FileSystemResource(path.toFile()));
    }

    @Transactional
    public int delete(List<Long> suppliedIds) throws IOException {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(suppliedIds == null ? Collections.emptyList() : suppliedIds));
        ids.removeIf(id -> id == null || id <= 0);
        if (ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("图片 ID 数量必须在 1 到 100 之间");
        List<Path> files = new ArrayList<>();
        for (Long id : ids) {
            Map<String,Object> row = repository.findById(id);
            if (row != null) files.add(storedPath(text(row.get("storagePath"), 1000)));
        }
        int deleted = repository.deleteByIds(ids);
        for (Path file : files) Files.deleteIfExists(file);
        return deleted;
    }

    private Map<String,Object> required(long id) {
        if (id <= 0) throw new IllegalArgumentException("图片 ID 不合法");
        Map<String,Object> row = repository.findById(id);
        if (row == null) throw new IllegalArgumentException("我的最爱图片不存在");
        return row;
    }
    private Path storedPath(String relative) {
        if (relative == null) throw new IllegalStateException("我的最爱记录缺少服务器存储路径");
        Path path = storageRoot.resolve(relative).normalize(); ensureWithinRoot(path); return path;
    }
    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) throw new IllegalArgumentException("我的最爱存储路径不合法");
    }
    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }
    private static String detectImageType(MultipartFile image) {
        byte[] header = new byte[12]; int read;
        try (InputStream input = image.getInputStream()) { read = input.read(header); }
        catch (IOException exception) { throw new IllegalArgumentException("无法读取上传图片", exception); }
        if (read >= 8 && header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47) return "image/png";
        if (read >= 3 && header[0] == (byte)0xff && header[1] == (byte)0xd8 && header[2] == (byte)0xff) return "image/jpeg";
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "image/webp";
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') return "image/gif";
        throw new IllegalArgumentException("当前仅支持真实的 PNG、JPEG、WEBP 或 GIF 图片");
    }
    private static String extension(String type) {
        if ("image/png".equals(type)) return ".png";
        if ("image/jpeg".equals(type)) return ".jpg";
        if ("image/webp".equals(type)) return ".webp";
        if ("image/gif".equals(type)) return ".gif";
        throw new IllegalArgumentException("不支持的图片类型");
    }
    private static String safeFileName(String value) {
        String name = value == null ? "" : Paths.get(value).getFileName().toString().trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return "favorite-image";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.'); return dot > 0 ? value.substring(0, dot) : value;
    }
    private static String text(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        String clean = value.trim(); return clean.length() > max ? clean.substring(0, max) : clean;
    }
    private static String text(Object value, int max) { return text(value == null ? null : String.valueOf(value), max); }
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
    private static FavoriteMediaVO toVO(Map<String,Object> row) {
        return new FavoriteMediaVO(number(row.get("id")), nullableLong(row.get("assetId")), text(row.get("originalFileName"),255),
                text(row.get("title"),255), text(row.get("mediaType"),32), text(row.get("contentType"),100), number(row.get("fileSize")),
                integer(row.get("width")), integer(row.get("height")), text(row.get("prompt"),16000), text(row.get("sourcePlatform"),20), date(row.get("createdAt")));
    }
    private static long number(Object value) { return value instanceof Number ? ((Number)value).longValue() : 0; }
    private static Long nullableLong(Object value) { return value instanceof Number ? ((Number)value).longValue() : null; }
    private static Integer integer(Object value) { return value instanceof Number ? ((Number)value).intValue() : null; }
    private static LocalDateTime date(Object value) { return value instanceof LocalDateTime ? (LocalDateTime)value : value instanceof Timestamp ? ((Timestamp)value).toLocalDateTime() : null; }
}
