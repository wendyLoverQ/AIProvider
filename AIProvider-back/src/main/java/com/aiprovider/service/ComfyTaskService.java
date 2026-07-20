package com.aiprovider.service;
import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import com.aiprovider.repository.ComfyTaskRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ComfyTaskService {
    private final ComfyTaskRepository repository;
    public ComfyTaskService(ComfyTaskRepository repository) { this.repository = repository; }

    public void save(ComfyTaskRecordDTO dto) {
        prepare(dto);
        repository.save(dto);
    }

    public void saveBatch(List<ComfyTaskRecordDTO> items) {
        if (items == null || items.isEmpty() || items.size() > 1000) throw new IllegalArgumentException("批量任务数量必须在 1 到 1000 之间");
        items.forEach(this::prepare);
        repository.saveBatch(items);
    }

    public Map<String,Object> duplicate(String workflowId, String hash) {
        validateHash(workflowId, hash);
        Map<String,Object> row = repository.findDuplicate(workflowId, hash.toLowerCase(Locale.ROOT));
        return row == null ? Collections.emptyMap() : row;
    }

    public List<String> duplicateHashes(String workflowId, List<String> hashes) {
        if (hashes == null || hashes.isEmpty() || hashes.size() > 1000) throw new IllegalArgumentException("SHA-256 列表数量必须在 1 到 1000 之间");
        List<String> normalized = new ArrayList<>();
        for (String hash : hashes) {
            validateHash(workflowId, hash);
            String value = hash.toLowerCase(Locale.ROOT);
            if (!normalized.contains(value)) normalized.add(value);
        }
        return repository.findDuplicateHashes(workflowId, normalized);
    }

    public void complete(String id, List<String> paths) {
        if (blank(id)) throw new IllegalArgumentException("任务 ID 不能为空");
        List<String> clean = paths == null ? Collections.emptyList() : paths.stream().filter(x -> !blank(x)).toList();
        String json = "[" + clean.stream().map(x -> "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").reduce((a,b) -> a + "," + b).orElse("") + "]";
        repository.complete(id, clean.isEmpty() ? null : clean.get(0), json);
    }

    private void prepare(ComfyTaskRecordDTO dto) {
        if (dto == null || blank(dto.getPromptId()) || blank(dto.getWorkflowId())) throw new IllegalArgumentException("任务 ID 和工作流不能为空");
        if (blank(dto.getWorkflowName())) dto.setWorkflowName(dto.getWorkflowId());
        if (blank(dto.getParametersJson())) dto.setParametersJson("{}");
        if (blank(dto.getStatus())) dto.setStatus("QUEUED");
    }

    private void validateHash(String workflowId, String hash) {
        if (blank(workflowId) || hash == null || !hash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("工作流和 SHA-256 无效");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
