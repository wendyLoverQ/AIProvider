package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import com.aiprovider.model.dto.LocalGeneratedImagePathsDTO;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.repository.LocalGeneratedImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class LocalGeneratedImageService {
    private final LocalGeneratedImageRepository repository;
    public LocalGeneratedImageService(LocalGeneratedImageRepository repository) { this.repository = repository; }

    @Transactional
    public int saveBatch(LocalGeneratedImageBatchDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<LocalGeneratedImageItemDTO> items = dto == null || dto.getItems() == null ? Collections.emptyList() : dto.getItems();
        if (items.isEmpty() || items.size() > 100) throw new IllegalArgumentException("本机生成图片记录数量必须在 1 到 100 之间");
        Set<String> paths = new HashSet<>();
        int saved = 0;
        for (LocalGeneratedImageItemDTO item : items) {
            validate(item);
            String path = item.getImagePath().trim().replace('\\', '/');
            String pathKey = "Windows".equals(platform) ? path.toLowerCase(Locale.ROOT) : path;
            if (!paths.add(pathKey)) throw new IllegalArgumentException("同一批次包含重复图片路径：" + path);
            item.setImagePath(path);
            item.setFileName(clean(item.getFileName(), 255));
            item.setWorkflowId(clean(item.getWorkflowId(), 100));
            item.setWorkflowName(clean(item.getWorkflowName(), 255));
            item.setPrompt(clean(item.getPrompt(), 16000));
            item.setNegativePrompt(clean(item.getNegativePrompt(), 16000));
            item.setLorasJson(clean(item.getLorasJson(), 16000));
            if (item.getGenerationDurationMs() != null && item.getGenerationDurationMs() < 0)
                throw new IllegalArgumentException("生成耗时不能为负数");
            saved += repository.upsert(platform, sha256(pathKey), item) > 0 ? 1 : 0;
        }
        return saved;
    }

    public GalleryRecordPageVO page(String platformValue, int page, int pageSize, String statusValue) {
        String platform = platform(platformValue);
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        String status = status(statusValue);
        long total = repository.count(platform, status);
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int currentPage = pages == 0 ? 1 : (int)Math.min(page, pages);
        return new GalleryRecordPageVO(repository.findPage(platform, status, pageSize, (currentPage - 1) * pageSize), total, currentPage, pageSize);
    }

    @Transactional
    public int trash(LocalGeneratedImagePathsDTO dto) {
        return repository.trash(platform(dto == null ? null : dto.getPlatform()), pathHashes(dto));
    }

    @Transactional
    public int restore(LocalGeneratedImagePathsDTO dto) {
        return repository.restore(platform(dto == null ? null : dto.getPlatform()), pathHashes(dto));
    }

    @Transactional
    public int delete(LocalGeneratedImagePathsDTO dto) {
        return repository.delete(platform(dto == null ? null : dto.getPlatform()), pathHashes(dto));
    }

    private static void validate(LocalGeneratedImageItemDTO item) {
        if (item == null) throw new IllegalArgumentException("本机生成图片记录不能为空");
        if (item.getPromptId() == null || item.getPromptId().trim().isEmpty() || item.getPromptId().length() > 100)
            throw new IllegalArgumentException("promptId 不能为空且不能超过 100 字符");
        if (item.getImagePath() == null || item.getImagePath().trim().isEmpty() || item.getImagePath().length() > 2000)
            throw new IllegalArgumentException("图片路径不能为空且不能超过 2000 字符");
    }
    private static String platform(String value) {
        if ("windows".equalsIgnoreCase(value)) return "Windows";
        if ("mac".equalsIgnoreCase(value) || "macos".equalsIgnoreCase(value)) return "macOS";
        throw new IllegalArgumentException("platform 仅支持 Windows 或 macOS");
    }
    private static String status(String value) {
        String status = value == null || value.trim().isEmpty() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("TRASHED"))
            throw new IllegalArgumentException("status 仅支持 ACTIVE 或 TRASHED");
        return status;
    }
    private static List<String> pathHashes(LocalGeneratedImagePathsDTO dto) {
        List<String> paths = dto == null || dto.getPaths() == null ? Collections.emptyList() : dto.getPaths();
        if (paths.isEmpty() || paths.size() > 100) throw new IllegalArgumentException("图片路径数量必须在 1 到 100 之间");
        String platform = platform(dto.getPlatform());
        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        for (String value : paths) {
            if (value == null || value.trim().isEmpty() || value.length() > 2000) throw new IllegalArgumentException("图片路径不能为空且不能超过 2000 字符");
            String path = value.trim().replace('\\', '/');
            String pathKey = "Windows".equals(platform) ? path.toLowerCase(Locale.ROOT) : path;
            hashes.add(sha256(pathKey));
        }
        return new ArrayList<>(hashes);
    }
    private static String clean(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("JVM 不支持 SHA-256", e); }
    }
}
