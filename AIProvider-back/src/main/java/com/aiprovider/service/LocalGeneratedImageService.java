package com.aiprovider.service;

import com.aiprovider.model.dto.LocalGeneratedImageBatchDTO;
import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
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
