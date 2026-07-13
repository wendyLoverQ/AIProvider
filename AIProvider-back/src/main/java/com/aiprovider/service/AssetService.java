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
    private final AssetRepository repository;
    public AssetService(AssetRepository repository) { this.repository = repository; }

    @Transactional
    public int saveBatch(AssetBatchDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<AssetItemDTO> items = dto == null || dto.getItems() == null ? Collections.emptyList() : dto.getItems();
        if (items.isEmpty() || items.size() > 500) throw new IllegalArgumentException("资产数量必须在 1 到 500 之间");
        int saved = 0;
        Set<String> paths = new HashSet<>();
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
            item.setLorasJson(clean(item.getLorasJson(), 16000));
            saved += repository.upsert(platform, sha256(pathKey), item) > 0 ? 1 : 0;
        }
        return saved;
    }

    public AssetPageVO page(String platform, int page, int pageSize) {
        String normalizedPlatform = platform(platform);
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        List<AssetVO> items = new ArrayList<>();
        for (Map<String,Object> row : repository.findPage(normalizedPlatform, pageSize, (page - 1) * pageSize)) items.add(toVO(row));
        return new AssetPageVO(items, repository.count(normalizedPlatform), page, pageSize);
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
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64); for (byte valueByte : bytes) result.append(String.format("%02x", valueByte)); return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM 不支持 SHA-256", e); }
    }
    private static AssetVO toVO(Map<String,Object> row) {
        return new AssetVO(number(row.get("id")), text(row.get("platform")), text(row.get("localPath")), text(row.get("localUrl")), text(row.get("fileName")), number(row.get("fileSize")),
                integer(row.get("width")), integer(row.get("height")), text(row.get("prompt")), text(row.get("negativePrompt")), text(row.get("lorasJson")), nullableLong(row.get("seed")), integer(row.get("steps")),
                decimal(row.get("cfg")), text(row.get("sampler")), text(row.get("scheduler")), text(row.get("workflowId")), date(row.get("generatedAt")), date(row.get("createdAt")));
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static long number(Object value) { return value instanceof Number ? ((Number)value).longValue() : 0; }
    private static Long nullableLong(Object value) { return value instanceof Number ? ((Number)value).longValue() : null; }
    private static Integer integer(Object value) { return value instanceof Number ? ((Number)value).intValue() : null; }
    private static Double decimal(Object value) { return value instanceof Number ? ((Number)value).doubleValue() : null; }
    private static LocalDateTime date(Object value) { return value instanceof LocalDateTime ? (LocalDateTime)value : value instanceof Timestamp ? ((Timestamp)value).toLocalDateTime() : null; }
}
