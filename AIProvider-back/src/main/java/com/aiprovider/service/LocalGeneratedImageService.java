package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageIdsDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import com.aiprovider.model.vo.GalleryRecordPageVO;
import com.aiprovider.model.vo.LocalGeneratedImageBatchResultVO;
import com.aiprovider.repository.LocalGeneratedImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class LocalGeneratedImageService {
    private static final Logger log = LoggerFactory.getLogger(LocalGeneratedImageService.class);
    private final LocalGeneratedImageRepository repository;
    public LocalGeneratedImageService(LocalGeneratedImageRepository repository) { this.repository = repository; }

    @Transactional
    public LocalGeneratedImageBatchResultVO saveBatch(LocalGeneratedImageBatchDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<LocalGeneratedImageItemDTO> items = dto == null || dto.getItems() == null ? Collections.emptyList() : dto.getItems();
        if (items.isEmpty() || items.size() > 100) throw new IllegalArgumentException("本机生成图片记录数量必须在 1 到 100 之间");
        Set<String> paths = new HashSet<>();
        List<String> pathHashes = new ArrayList<>();
        List<Map<String,Object>> rows = new ArrayList<>();
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
            String pathHash = sha256(pathKey);
            pathHashes.add(pathHash);
            rows.add(Map.of("pathHash", pathHash, "item", item));
        }
        repository.upsertBatch(platform, rows);
        List<Map<String,Object>> persisted = repository.findByPathHashes(platform, pathHashes);
        if (persisted.size() != rows.size()) {
            log.warn("local_image_batch_save_mismatch platform={} requested={} persisted={} ids={}", platform, rows.size(), persisted.size(), recordIds(persisted));
            throw new IllegalStateException("本机图片批量保存后未返回全部数据库 ID");
        }
        log.info("local_image_batch_saved platform={} requested={} saved={} ids={}", platform, items.size(), rows.size(), recordIds(persisted));
        return new LocalGeneratedImageBatchResultVO(rows.size(), persisted);
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
    public int trash(LocalGeneratedImageIdsDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<Long> ids = validIds(dto);
        int affected = repository.trash(platform, ids);
        logMutation("trash", platform, ids, affected);
        return affected;
    }

    @Transactional
    public int restore(LocalGeneratedImageIdsDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<Long> ids = validIds(dto);
        int affected = repository.restore(platform, ids);
        logMutation("restore", platform, ids, affected);
        return affected;
    }

    @Transactional
    public int delete(LocalGeneratedImageIdsDTO dto) {
        String platform = platform(dto == null ? null : dto.getPlatform());
        List<Long> ids = validIds(dto);
        int affected = repository.delete(platform, ids);
        logMutation("delete", platform, ids, affected);
        return affected;
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
    private static List<Long> validIds(LocalGeneratedImageIdsDTO dto) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(dto == null || dto.getIds() == null
                ? Collections.emptyList() : dto.getIds()));
        ids.removeIf(id -> id == null || id <= 0);
        if (ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("本机图片 ID 数量必须在 1 到 100 之间");
        return ids;
    }
    private static List<Long> recordIds(List<Map<String,Object>> rows) {
        List<Long> ids = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            Object value = row.get("id");
            if (value instanceof Number) ids.add(((Number) value).longValue());
        }
        return ids;
    }
    private static void logMutation(String operation, String platform, List<Long> ids, int affected) {
        if (affected == ids.size()) log.info("local_image_mutation operation={} platform={} ids={} affected={}", operation, platform, ids, affected);
        else {
            log.warn("local_image_mutation_mismatch operation={} platform={} ids={} requested={} affected={}", operation, platform, ids, ids.size(), affected);
            throw new IllegalStateException("本机图片批量" + operation + "影响行数不一致：请求 " + ids.size() + "，实际 " + affected);
        }
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
