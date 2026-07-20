package com.aiprovider.repository;
import com.aiprovider.mapper.ComfyTaskMapper;
import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ComfyTaskRepository {
    private final ComfyTaskMapper mapper;
    public ComfyTaskRepository(ComfyTaskMapper mapper) { this.mapper = mapper; }
    public int save(ComfyTaskRecordDTO dto) { return mapper.save(dto); }
    public int saveBatch(List<ComfyTaskRecordDTO> items) { return mapper.saveBatch(items); }
    public Map<String,Object> findDuplicate(String workflowId, String hash) { return mapper.findDuplicate(workflowId, hash); }
    public List<String> findDuplicateHashes(String workflowId, List<String> hashes) { return mapper.findDuplicateHashes(workflowId, hashes); }
    public int complete(String id, String output, String json) { return mapper.complete(id, output, json); }
}
