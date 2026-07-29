package com.aiprovider.service;

import com.aiprovider.model.vo.ComfyWorkflowVO;
import com.aiprovider.repository.ComfyWorkflowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ComfyWorkflowService {
    private final ComfyWorkflowRepository repository;
    private final ObjectMapper json;
    public ComfyWorkflowService(ComfyWorkflowRepository repository, ObjectMapper json) { this.repository = repository; this.json = json; }

    public List<ComfyWorkflowVO> list() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ComfyWorkflowService.list", new String[] {}, new Object[] {});
    List<ComfyWorkflowVO> result = new ArrayList<>();
        for (Map<String, Object> row : repository.findActive()) {
            result.add(new ComfyWorkflowVO(text(row.get("id")), text(row.get("name")), text(row.get("description")),
                    parse(row.get("definitionJson")), parse(row.get("bindingJson")), parse(row.get("defaultsJson"))));
        }
        return com.aiprovider.logging.BusinessOperationLogger.success("service.ComfyWorkflowService.list", result);
    }
    private Map<String, Object> parse(Object value) {
        try { return json.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("数据库中的工作流 JSON 无效", e); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
}
