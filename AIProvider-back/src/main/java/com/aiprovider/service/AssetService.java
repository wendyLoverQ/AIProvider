package com.aiprovider.service;

import com.aiprovider.model.dto.*;
import com.aiprovider.model.vo.*;
import com.aiprovider.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssetService {
    private static final Set<String> ASSET_TYPES = new HashSet<>(Arrays.asList("image", "video", "audio", "document", "other"));
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("ACTIVE", "PENDING"));
    private final AssetRepository repository;
    public AssetService(AssetRepository repository) { this.repository = repository; }

    @Transactional
    public AssetBatchResultVO saveBatch(AssetBatchDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<AssetItemDTO> items = dto == null || dto.getItems() == null ? Collections.emptyList() : dto.getItems();
        if (items.isEmpty() || items.size() > 500) throw new IllegalArgumentException("资产数量必须在 1 到 500 之间");
        int saved = 0;
        Set<String> paths = new HashSet<>();
        List<String> pathHashes = new ArrayList<>();
        for (AssetItemDTO item : items) {
            validate(item);
            String normalizedPath = item.getLocalPath().trim();
            String pathKey = "Windows".equals(platform) ? normalizedPath.toLowerCase(Locale.ROOT) : normalizedPath;
            if (!paths.add(pathKey)) continue;
            item.setLocalPath(normalizedPath);
            item.setLocalUrl(clean(item.getLocalUrl(), 2500));
            if (item.getLocalUrl() == null) throw new IllegalArgumentException("资产本机 URL 不能为空");
            item.setFileName(clean(item.getFileName(), 255));
            if (item.getFileName() == null) item.setFileName(fileName(normalizedPath));
            item.setFileSize(item.getFileSize() == null ? 0 : Math.max(0, item.getFileSize()));
            String mimeType = clean(item.getMimeType(), 100);
            if (mimeType == null) mimeType = inferMimeType(item.getFileName());
            item.setMimeType(mimeType);
            String assetType = clean(item.getAssetType(), 32);
            assetType = assetType == null ? inferAssetType(mimeType) : assetType.toLowerCase(Locale.ROOT);
            if (!ASSET_TYPES.contains(assetType)) throw new IllegalArgumentException("assetType 仅支持 image、video、audio、document 或 other");
            item.setAssetType(assetType);
            String status = clean(item.getStatus(), 16);
            if (status != null && !VALID_STATUSES.contains(status.toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("status 仅支持 ACTIVE 或 PENDING");
            item.setStatus(status == null ? "ACTIVE" : status.toUpperCase(Locale.ROOT));
            if (item.getGenerationDurationMs() != null) item.setGenerationDurationMs(Math.max(0, item.getGenerationDurationMs()));
            item.setLorasJson(clean(item.getLorasJson(), 16000));
            String pathHash = sha256(pathKey);
            saved += repository.upsert(platform, pathHash, item) > 0 ? 1 : 0;
            pathHashes.add(pathHash);
        }
        List<AssetVO> persisted = new ArrayList<>();
        for (Map<String,Object> row : repository.findByPathHashes(platform, pathHashes)) persisted.add(toVO(row));
        return new AssetBatchResultVO(saved, persisted);
    }

    public AssetPageVO page(String platform, int page, int pageSize, String status) {
        String normalizedPlatform = platform(platform);
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        String normalizedStatus = status == null || status.trim().isEmpty() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !VALID_STATUSES.contains(normalizedStatus))
            throw new IllegalArgumentException("status 仅支持 ACTIVE 或 PENDING");
        List<AssetVO> items = new ArrayList<>();
        for (Map<String,Object> row : repository.findPage(normalizedPlatform, normalizedStatus, pageSize, (page - 1) * pageSize)) items.add(toVO(row));
        return new AssetPageVO(items, repository.count(normalizedPlatform, normalizedStatus), page, pageSize);
    }

    public List<AssetPromptVO> imagePromptPool(String platform) {
        List<AssetPromptVO> result = new ArrayList<>();
        for (Map<String,Object> row : repository.findImagePromptPool(platform(platform)))
            result.add(new AssetPromptVO(text(row.get("prompt")), text(row.get("negativePrompt")), number(row.get("weight"))));
        return result;
    }

    @Transactional
    public int delete(AssetDeleteDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<Long> ids = dto == null || dto.getIds() == null ? Collections.emptyList() : dto.getIds();
        List<Long> valid = new ArrayList<>(new LinkedHashSet<>(ids));
        valid.removeIf(id -> id == null || id <= 0);
        if (valid.isEmpty() || valid.size() > 500) throw new IllegalArgumentException("资产 ID 数量必须在 1 到 500 之间");
        return repository.deleteByIds(platform, valid);
    }

    @Transactional
    public int updateStatus(AssetStatusDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<Long> ids = dto == null || dto.getIds() == null ? Collections.emptyList() : dto.getIds();
        List<Long> valid = new ArrayList<>(new LinkedHashSet<>(ids));
        valid.removeIf(id -> id == null || id <= 0);
        if (valid.isEmpty() || valid.size() > 500) throw new IllegalArgumentException("资产 ID 数量必须在 1 到 500 之间");
        String status = dto == null || dto.getStatus() == null ? null : dto.getStatus().trim().toUpperCase(Locale.ROOT);
        if (status == null || !VALID_STATUSES.contains(status))
            throw new IllegalArgumentException("status 仅支持 ACTIVE 或 PENDING");
        return repository.updateStatus(platform, valid, status);
    }

    private static void validate(AssetItemDTO item) {
        if (item == null || item.getLocalPath() == null || item.getLocalPath().trim().isEmpty() || item.getLocalPath().length() > 2000)
            throw new IllegalArgumentException("资产本机路径不能为空且不能超过 2000 字符");
        if (item.getWidth() != null && item.getWidth() < 0 || item.getHeight() != null && item.getHeight() < 0)
            throw new IllegalArgumentException("资产尺寸不能为负数");
    }
    private static String platform(String value) {
        String text = value == null ? "" : value.trim();
        if ("windows".equalsIgnoreCase(text)) return "Windows";
        if ("mac".equalsIgnoreCase(text) || "macos".equalsIgnoreCase(text)) return "macOS";
        throw new IllegalArgumentException("platform 仅支持 Windows 或 macOS");
    }
    private static String clean(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim(); return text.length() <= max ? text : text.substring(0, max);
    }
    private static String fileName(String path) {
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return index >= 0 ? path.substring(index + 1) : path;
    }
    private static String inferAssetType(String mimeType) {
        if (mimeType != null) {
            if ("application/octet-stream".equalsIgnoreCase(mimeType)) return "other";
            int separator = mimeType.indexOf('/');
            String major = separator > 0 ? mimeType.substring(0, separator).toLowerCase(Locale.ROOT) : mimeType.toLowerCase(Locale.ROOT);
            if (Arrays.asList("image", "video", "audio").contains(major)) return major;
            if (Arrays.asList("application", "text").contains(major)) return "document";
        }
        return "other";
    }
    private static String inferMimeType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64); for (byte valueByte : bytes) result.append(String.format("%02x", valueByte)); return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM 不支持 SHA-256", e); }
    }
    private static AssetVO toVO(Map<String,Object> row) {
        return new AssetVO(number(row.get("id")), text(row.get("platform")), text(row.get("localPath")), text(row.get("localUrl")), text(row.get("fileName")), number(row.get("fileSize")),
                integer(row.get("width")), integer(row.get("height")), text(row.get("assetType")), text(row.get("mimeType")), text(row.get("status")), text(row.get("prompt")), text(row.get("negativePrompt")), text(row.get("lorasJson")), nullableLong(row.get("seed")), integer(row.get("steps")),
                decimal(row.get("cfg")), text(row.get("sampler")), text(row.get("scheduler")), text(row.get("workflowId")), date(row.get("generatedAt")),
                date(row.get("generationCompletedAt")), nullableLong(row.get("generationDurationMs")), date(row.get("createdAt")));
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static long number(Object value) { return value instanceof Number ? ((Number)value).longValue() : 0; }
    private static Long nullableLong(Object value) { return value instanceof Number ? ((Number)value).longValue() : null; }
    private static Integer integer(Object value) { return value instanceof Number ? ((Number)value).intValue() : null; }
    private static Double decimal(Object value) { return value instanceof Number ? ((Number)value).doubleValue() : null; }
    private static LocalDateTime date(Object value) { return value instanceof LocalDateTime ? (LocalDateTime)value : value instanceof Timestamp ? ((Timestamp)value).toLocalDateTime() : null; }
}