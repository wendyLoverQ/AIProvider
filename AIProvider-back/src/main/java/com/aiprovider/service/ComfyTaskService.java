package com.aiprovider.service;
import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import com.aiprovider.repository.ComfyTaskRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class ComfyTaskService {
    private static final Logger log = LoggerFactory.getLogger(ComfyTaskService.class);
    private final ComfyTaskRepository repository;
    public ComfyTaskService(ComfyTaskRepository repository) { this.repository = repository; }

    public void save(ComfyTaskRecordDTO dto) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyTaskService.save", new String[] { "dto" }, new Object[] { dto });
    prepare(dto);
        int affected = repository.save(dto);
        logMutation("save", Collections.singletonList(dto.getPromptId()), 1, affected);
        com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyTaskService.save", null);
    }

    public void saveBatch(List<ComfyTaskRecordDTO> items) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyTaskService.saveBatch", new String[] { "items" }, new Object[] { items });
    if (items == null || items.isEmpty() || items.size() > 1000) throw new IllegalArgumentException("批量任务数量必须在 1 到 1000 之间");
        items.forEach(this::prepare);
        int affected = repository.saveBatch(items);
        logMutation("saveBatch", items.stream().map(ComfyTaskRecordDTO::getPromptId).toList(), items.size(), affected);
        com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyTaskService.saveBatch", null);
    }

    public Map<String,Object> duplicate(String workflowId, String hash) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyTaskService.duplicate", new String[] { "workflowId", "hash" }, new Object[] { workflowId, hash });
    validateHash(workflowId, hash);
        Map<String,Object> row = repository.findDuplicate(workflowId, hash.toLowerCase(Locale.ROOT));
        return com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyTaskService.duplicate", row == null ? Collections.emptyMap() : row);
    }

    public List<String> duplicateHashes(String workflowId, List<String> hashes) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyTaskService.duplicateHashes", new String[] { "workflowId", "hashes" }, new Object[] { workflowId, hashes });
    if (hashes == null || hashes.isEmpty() || hashes.size() > 1000) throw new IllegalArgumentException("SHA-256 列表数量必须在 1 到 1000 之间");
        List<String> normalized = new ArrayList<>();
        for (String hash : hashes) {
            validateHash(workflowId, hash);
            String value = hash.toLowerCase(Locale.ROOT);
            if (!normalized.contains(value)) normalized.add(value);
        }
        return com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyTaskService.duplicateHashes", repository.findDuplicateHashes(workflowId, normalized));
    }

    public void complete(String id, List<String> paths) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyTaskService.complete", new String[] { "id", "paths" }, new Object[] { id, paths });
    if (blank(id)) throw new IllegalArgumentException("任务 ID 不能为空");
        List<String> clean = paths == null ? Collections.emptyList() : paths.stream().filter(x -> !blank(x)).toList();
        String json = "[" + clean.stream().map(x -> "\"" + x.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").reduce((a,b) -> a + "," + b).orElse("") + "]";
        repository.complete(id, clean.isEmpty() ? null : clean.get(0), json);
        com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyTaskService.complete", null);
    }

    private void prepare(ComfyTaskRecordDTO dto) {
        if (dto == null || blank(dto.getPromptId()) || blank(dto.getWorkflowId())) throw new IllegalArgumentException("任务 ID 和工作流不能为空");
        if (blank(dto.getWorkflowName())) dto.setWorkflowName(dto.getWorkflowId());
        if (blank(dto.getParametersJson())) dto.setParametersJson("{}");
        if (blank(dto.getStatus())) dto.setStatus("QUEUED");
        String promptMode = blank(dto.getPromptMode()) ? "tags" : dto.getPromptMode().trim().toLowerCase(Locale.ROOT);
        if (!"tags".equals(promptMode) && !"prose".equals(promptMode)) throw new IllegalArgumentException("Prompt 类型只能是 tags 或 prose");
        dto.setPromptMode(promptMode);
    }

    private void validateHash(String workflowId, String hash) {
        if (blank(workflowId) || hash == null || !hash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("工作流和 SHA-256 无效");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static void logMutation(String operation, List<String> ids, int requested, int affected) {
        if (affected == requested) log.info("comfy_task_mutation operation={} ids={} requestedCount={} affectedRows={}", operation, ids, requested, affected);
        else {
            log.warn("comfy_task_mutation_mismatch operation={} ids={} requestedCount={} affectedRows={}", operation, ids, requested, affected);
            throw new IllegalStateException("生成任务保存影响行数不一致");
        }
    }
}
