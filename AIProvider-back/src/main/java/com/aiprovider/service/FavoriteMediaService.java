package com.aiprovider.service;

import com.aiprovider.mapper.FavoriteMediaMapper;
import com.aiprovider.model.dto.FavoriteMediaBatchItemDTO;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FavoriteMediaService {
    private static final long MAX_MEDIA_BYTES = 500L * 1024 * 1024;
    private static final String TEMP_PREFIX = ".favorite-upload-";
    private static final int THUMBNAIL_MAX_EDGE = 640;
    private static final float THUMBNAIL_JPEG_QUALITY = 0.92f;
    private static final String THUMBNAIL_DIR = "thumbnails";
    private static final String THUMBNAIL_FORMAT = "jpg";
    private final FavoriteMediaRepository repository;
    private final AssetRepository assetRepository;
    private final Path storageRoot;
    private final String ffmpegPath;

    public FavoriteMediaService(FavoriteMediaRepository repository, AssetRepository assetRepository,
                                @Value("${favorites.storage-directory:/opt/aiprovider/favorites}") String storageDirectory,
                                @Value("${favorites.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        if (storageDirectory == null || storageDirectory.trim().isEmpty()) throw new IllegalArgumentException("我的最爱存储目录不能为空");
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.storageRoot = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.ffmpegPath = ffmpegPath == null || ffmpegPath.trim().isEmpty() ? "ffmpeg" : ffmpegPath.trim();
    }

    @Transactional
    public FavoriteMediaVO upload(MultipartFile file, Long assetId, String title, Integer width, Integer height,
                                  String prompt, String sourcePlatform) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
        if (file.getSize() > MAX_MEDIA_BYTES) throw new IllegalArgumentException("单个文件不能超过 500MB");
        if (assetId != null && assetId > 0 && assetRepository.findById(assetId) == null)
            throw new IllegalArgumentException("来源资产不存在：" + assetId);
        if (width != null && width < 0 || height != null && height < 0) throw new IllegalArgumentException("媒体尺寸不能为负数");
        String[] detected = detectMediaType(file);
        String mediaType = detected[0];
        String contentType = detected[1];
        String originalName = safeFileName(file.getOriginalFilename());
        String normalizedTitle = text(title, 255);
        if (normalizedTitle == null) normalizedTitle = stripExtension(originalName);
        String normalizedPlatform = text(sourcePlatform, 20);
        if (normalizedPlatform != null && !Arrays.asList("Windows", "macOS").contains(normalizedPlatform))
            throw new IllegalArgumentException("来源平台仅支持 Windows 或 macOS");

        Files.createDirectories(storageRoot);
        Path temporary = storageRoot.resolve(TEMP_PREFIX + UUID.randomUUID() + ".tmp").normalize();
        ensureWithinRoot(temporary);
        Path target = null, thumbnailFile = null;
        boolean targetCreated = false;
        try {
            file.transferTo(temporary);
            String sha256 = sha256(temporary);
            Map<String,Object> existing = repository.findBySha256(sha256);
            if (existing != null) return toVO(existing);
            target = storageRoot.resolve(sha256.substring(0, 2)).resolve(sha256 + extension(contentType)).normalize();
            ensureWithinRoot(target);
            thumbnailFile = thumbnailPath(sha256);
            FavoriteMediaMapper.Row row = new FavoriteMediaMapper.Row();
            row.setAssetId(assetId != null && assetId > 0 ? assetId : null);
            row.setStoragePath(storageRoot.relativize(target).toString().replace('\\', '/'));
            row.setOriginalFileName(originalName);
            row.setTitle(normalizedTitle);
            row.setMediaType(mediaType);
            row.setContentType(contentType);
            row.setFileSize(Files.size(temporary));
            row.setSha256(sha256);
            // 视频没有 width/height 时留空，由后端在生成缩略图时尝试解析
            row.setWidth(width); row.setHeight(height);
            row.setPrompt(text(prompt, 16000)); row.setSourcePlatform(normalizedPlatform);
            // 先生成缩略图，失败不阻塞上传
            String thumbnailRelative = null;
            try {
                thumbnailRelative = generateThumbnail(temporary, contentType, thumbnailFile, sha256, row);
            } catch (Exception exception) {
                // 缩略图失败不阻断主流程，仅留空 ThumbnailPath
                thumbnailFile = null;
            }
            row.setThumbnailPath(thumbnailRelative);
            repository.insert(row);
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) Files.delete(temporary);
            else { moveAtomically(temporary, target); targetCreated = true; }
            Map<String,Object> saved = repository.findById(row.getId());
            if (saved == null) throw new IllegalStateException("我的最爱记录保存后无法读取");
            return toVO(saved);
        } catch (RuntimeException | IOException exception) {
            if (targetCreated && target != null) Files.deleteIfExists(target);
            if (thumbnailFile != null) Files.deleteIfExists(thumbnailFile);
            throw exception;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Transactional
    public int uploadBatch(List<MultipartFile> files, String metadataJson) throws IOException {
        if (files == null || files.isEmpty() || files.size() > 100) throw new IllegalArgumentException("批量文件数量必须在 1 到 100 之间");
        final List<FavoriteMediaBatchItemDTO> metadata;
        try {
            metadata = new ObjectMapper().readValue(metadataJson, new TypeReference<List<FavoriteMediaBatchItemDTO>>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("批量文件元数据不是有效 JSON", exception);
        }
        if (metadata.size() != files.size()) throw new IllegalArgumentException("批量文件与元数据数量不一致");
        List<Long> assetIds = metadata.stream().map(FavoriteMediaBatchItemDTO::getAssetId).filter(id -> id != null && id > 0).distinct().toList();
        if (!assetIds.isEmpty()) {
            Set<Long> existingIds = new HashSet<>(assetRepository.findExistingIds(assetIds));
            if (!existingIds.containsAll(assetIds)) throw new IllegalArgumentException("批量文件包含不存在的来源资产");
        }

        Files.createDirectories(storageRoot);
        List<StagedFavorite> staged = new ArrayList<>();
        List<Path> createdFiles = new ArrayList<>();
        try {
            for (int index = 0; index < files.size(); index++) {
                MultipartFile file = files.get(index);
                FavoriteMediaBatchItemDTO item = metadata.get(index);
                if (file == null || file.isEmpty()) throw new IllegalArgumentException("批量文件不能为空");
                if (file.getSize() > MAX_MEDIA_BYTES) throw new IllegalArgumentException("单个文件不能超过 500MB");
                if (item.getWidth() != null && item.getWidth() < 0 || item.getHeight() != null && item.getHeight() < 0)
                    throw new IllegalArgumentException("媒体尺寸不能为负数");
                String platform = text(item.getSourcePlatform(), 20);
                if (platform != null && !Arrays.asList("Windows", "macOS").contains(platform))
                    throw new IllegalArgumentException("来源平台仅支持 Windows 或 macOS");
                String[] detected = detectMediaType(file);
                String originalName = safeFileName(file.getOriginalFilename());
                Path temporary = storageRoot.resolve(TEMP_PREFIX + UUID.randomUUID() + ".tmp").normalize();
                ensureWithinRoot(temporary);
                file.transferTo(temporary);
                String hash = sha256(temporary);
                Path target = storageRoot.resolve(hash.substring(0, 2)).resolve(hash + extension(detected[1])).normalize();
                ensureWithinRoot(target);
                FavoriteMediaMapper.Row row = new FavoriteMediaMapper.Row();
                row.setAssetId(item.getAssetId() != null && item.getAssetId() > 0 ? item.getAssetId() : null);
                row.setStoragePath(storageRoot.relativize(target).toString().replace('\\', '/'));
                row.setOriginalFileName(originalName);
                String title = text(item.getTitle(), 255);
                row.setTitle(title == null ? stripExtension(originalName) : title);
                row.setMediaType(detected[0]); row.setContentType(detected[1]); row.setFileSize(Files.size(temporary)); row.setSha256(hash);
                row.setWidth(item.getWidth()); row.setHeight(item.getHeight()); row.setPrompt(text(item.getPrompt(), 16000)); row.setSourcePlatform(platform);
                staged.add(new StagedFavorite(temporary, target, thumbnailPath(hash), hash, row));
            }

            List<String> hashes = staged.stream().map(item -> item.sha256).distinct().toList();
            Set<String> existingHashes = new HashSet<>(repository.findExistingSha256s(hashes));
            Set<String> scheduledHashes = new HashSet<>();
            List<StagedFavorite> inserts = staged.stream()
                    .filter(item -> !existingHashes.contains(item.sha256) && scheduledHashes.add(item.sha256)).toList();
            for (StagedFavorite item : inserts) {
                boolean thumbnailExisted = Files.exists(item.thumbnail);
                String relative;
                try {
                    relative = generateThumbnail(item.temporary, item.row.getContentType(), item.thumbnail, item.sha256, item.row);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("批量媒体缩略图生成被中断", exception);
                }
                if (relative == null) throw new IOException("批量媒体缩略图生成失败：" + item.row.getOriginalFileName());
                item.row.setThumbnailPath(relative);
                if (!thumbnailExisted && Files.exists(item.thumbnail)) createdFiles.add(item.thumbnail);
            }
            if (!inserts.isEmpty()) repository.insertBatch(inserts.stream().map(item -> item.row).toList());
            for (StagedFavorite item : inserts) {
                Files.createDirectories(item.target.getParent());
                if (Files.exists(item.target)) Files.deleteIfExists(item.temporary);
                else { moveAtomically(item.temporary, item.target); createdFiles.add(item.target); }
            }
            return files.size();
        } catch (RuntimeException | IOException exception) {
            for (Path path : createdFiles) Files.deleteIfExists(path);
            throw exception;
        } finally {
            for (StagedFavorite item : staged) Files.deleteIfExists(item.temporary);
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

    public FavoriteMediaContent thumbnail(long id) throws IOException {
        Map<String,Object> row = required(id);
        String thumbnailRelative = text(row.get("thumbnailPath"), 1000);
        if (thumbnailRelative == null) {
            // 没有缩略图时回退到原图；视频会回退到 404 由前端兜底
            return content(id);
        }
        Path path = storedPath(thumbnailRelative);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("我的最爱缩略图文件不存在");
        String fileName = "thumbnail-" + id + "." + THUMBNAIL_FORMAT;
        return new FavoriteMediaContent(fileName, "image/jpeg", Files.size(path), new FileSystemResource(path.toFile()));
    }

    @Transactional
    public int delete(List<Long> suppliedIds) throws IOException {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(suppliedIds == null ? Collections.emptyList() : suppliedIds));
        ids.removeIf(id -> id == null || id <= 0);
        if (ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("媒体 ID 数量必须在 1 到 100 之间");
        List<Path> files = new ArrayList<>();
        for (Long id : ids) {
            Map<String,Object> row = repository.findById(id);
            if (row != null) {
                files.add(storedPath(text(row.get("storagePath"), 1000)));
                String thumb = text(row.get("thumbnailPath"), 1000);
                if (thumb != null) files.add(storedPath(thumb));
            }
        }
        int deleted = repository.deleteByIds(ids);
        for (Path file : files) Files.deleteIfExists(file);
        return deleted;
    }

    private Map<String,Object> required(long id) {
        if (id <= 0) throw new IllegalArgumentException("媒体 ID 不合法");
        Map<String,Object> row = repository.findById(id);
        if (row == null) throw new IllegalArgumentException("我的最爱记录不存在");
        return row;
    }
    private Path storedPath(String relative) {
        if (relative == null) throw new IllegalStateException("我的最爱记录缺少服务器存储路径");
        Path path = storageRoot.resolve(relative).normalize(); ensureWithinRoot(path); return path;
    }
    private Path thumbnailPath(String sha256) {
        Path dir = storageRoot.resolve(THUMBNAIL_DIR).resolve(sha256.substring(0, 2)).toAbsolutePath().normalize();
        return dir.resolve(sha256 + "." + THUMBNAIL_FORMAT).normalize();
    }
    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) throw new IllegalArgumentException("我的最爱存储路径不合法");
    }
    private static void moveAtomically(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { Files.move(source, target); }
    }
    /** 返回 [mediaType, contentType]，mediaType ∈ {image, video} */
    private static String[] detectMediaType(MultipartFile file) {
        byte[] header = new byte[24]; int read;
        try (InputStream input = file.getInputStream()) { read = input.read(header); }
        catch (IOException exception) { throw new IllegalArgumentException("无法读取上传文件", exception); }
        if (read >= 8 && header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47) return new String[]{"image", "image/png"};
        if (read >= 3 && header[0] == (byte)0xff && header[1] == (byte)0xd8 && header[2] == (byte)0xff) return new String[]{"image", "image/jpeg"};
        if (read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return new String[]{"image", "image/webp"};
        if (read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') return new String[]{"image", "image/gif"};
        // MP4 / MOV / WebM 视频魔数
        if (read >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            String brand = new String(header, 8, 4);
            if (brand.equals("mp42") || brand.equals("isom") || brand.equals("avc1") || brand.equals("M4V ") || brand.equals("qt  ")) return new String[]{"video", brand.equals("qt  ") ? "video/quicktime" : "video/mp4"};
        }
        if (read >= 4 && header[0] == 0x1a && header[1] == 0x45 && header[2] == (byte)0xdf && header[3] == (byte)0xa3) return new String[]{"video", "video/webm"};
        throw new IllegalArgumentException("当前仅支持 PNG、JPEG、WEBP、GIF 图片或 MP4、WEBM、MOV 视频");
    }
    private static String extension(String type) {
        if ("image/png".equals(type)) return ".png";
        if ("image/jpeg".equals(type)) return ".jpg";
        if ("image/webp".equals(type)) return ".webp";
        if ("image/gif".equals(type)) return ".gif";
        if ("video/mp4".equals(type)) return ".mp4";
        if ("video/webm".equals(type)) return ".webm";
        if ("video/quicktime".equals(type)) return ".mov";
        throw new IllegalArgumentException("不支持的媒体类型：" + type);
    }
    private static String safeFileName(String value) {
        String name = value == null ? "" : Paths.get(value).getFileName().toString().trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return "favorite-media";
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
    /** 生成缩略图：图片用 ImageIO 等比缩放；视频用 FFmpeg 抽帧后再缩放。返回相对存储路径或 null。 */
    private String generateThumbnail(Path source, String contentType, Path target, String sha256, FavoriteMediaMapper.Row row) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        if (contentType.startsWith("image/")) {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) return null;
            if (row.getWidth() == null || row.getHeight() == null) { row.setWidth(original.getWidth()); row.setHeight(original.getHeight()); }
            int[] size = scaleToFit(original.getWidth(), original.getHeight());
            BufferedImage thumb = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            paintScaled(thumb, original, size[0], size[1]);
            if (!writeHighQualityJpeg(thumb, target)) return null;
            return storageRoot.relativize(target).toString().replace('\\', '/');
        }
        if (contentType.startsWith("video/")) {
            // 用 FFmpeg 在 1 秒处抽帧，再走 ImageIO 缩放
            Path rawFrame = target.resolveSibling(sha256 + ".raw.png").normalize();
            try {
                ProcessBuilder builder = new ProcessBuilder(ffmpegPath, "-y", "-ss", "1", "-i", source.toString(),
                        "-frames:v", "1", "-vf", "scale=" + THUMBNAIL_MAX_EDGE + ":-2", rawFrame.toString());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                // 必须读完 stdout/stderr 合并流，否则进程会阻塞
                try (InputStream stream = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    while (stream.read(buffer) >= 0) { /* drain */ }
                }
                int exit = process.waitFor();
                if (exit != 0 || !Files.isRegularFile(rawFrame)) return null;
                BufferedImage frame = ImageIO.read(rawFrame.toFile());
                if (frame == null) return null;
                if (row.getWidth() == null || row.getHeight() == null) { row.setWidth(frame.getWidth()); row.setHeight(frame.getHeight()); }
                int[] size = scaleToFit(frame.getWidth(), frame.getHeight());
                BufferedImage thumb = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                paintScaled(thumb, frame, size[0], size[1]);
                if (!writeHighQualityJpeg(thumb, target)) return null;
                return storageRoot.relativize(target).toString().replace('\\', '/');
            } finally { Files.deleteIfExists(rawFrame); }
        }
        return null;
    }
    private static void paintScaled(BufferedImage thumb, BufferedImage source, int width, int height) {
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally { g.dispose(); }
    }
    /** 以高质量 JPEG（quality 0.92）写入文件，避免默认压缩太狠 */
    private static boolean writeHighQualityJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(THUMBNAIL_FORMAT).next();
        try (FileImageOutputStream output = new FileImageOutputStream(target.toFile())) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(THUMBNAIL_JPEG_QUALITY);
            writer.setOutput(output);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally { writer.dispose(); }
        return true;
    }
    private static int[] scaleToFit(int width, int height) {
        if (width <= 0 || height <= 0) return new int[]{THUMBNAIL_MAX_EDGE, THUMBNAIL_MAX_EDGE};
        int maxEdge = Math.max(width, height);
        if (maxEdge <= THUMBNAIL_MAX_EDGE) return new int[]{width, height};
        int newWidth = Math.round(width * (THUMBNAIL_MAX_EDGE / (float) maxEdge));
        int newHeight = Math.round(height * (THUMBNAIL_MAX_EDGE / (float) maxEdge));
        return new int[]{Math.max(1, newWidth), Math.max(1, newHeight)};
    }
    private static FavoriteMediaVO toVO(Map<String,Object> row) {
        return new FavoriteMediaVO(number(row.get("id")), nullableLong(row.get("assetId")), text(row.get("originalFileName"),255),
                text(row.get("title"),255), text(row.get("mediaType"),32), text(row.get("contentType"),100), number(row.get("fileSize")),
                integer(row.get("width")), integer(row.get("height")), text(row.get("prompt"),16000), text(row.get("sourcePlatform"),20),
                date(row.get("createdAt")), row.get("thumbnailPath") != null);
    }
    private static final class StagedFavorite {
        private final Path temporary, target, thumbnail;
        private final String sha256;
        private final FavoriteMediaMapper.Row row;
        private StagedFavorite(Path temporary, Path target, Path thumbnail, String sha256, FavoriteMediaMapper.Row row) {
            this.temporary = temporary; this.target = target; this.thumbnail = thumbnail; this.sha256 = sha256; this.row = row;
        }
    }
    private static long number(Object value) { return value instanceof Number ? ((Number)value).longValue() : 0; }
    private static Long nullableLong(Object value) { return value instanceof Number ? ((Number)value).longValue() : null; }
    private static Integer integer(Object value) { return value instanceof Number ? ((Number)value).intValue() : null; }
    private static LocalDateTime date(Object value) { return value instanceof LocalDateTime ? (LocalDateTime)value : value instanceof Timestamp ? ((Timestamp)value).toLocalDateTime() : null; }
}
