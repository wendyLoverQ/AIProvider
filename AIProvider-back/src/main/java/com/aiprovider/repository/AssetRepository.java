package com.aiprovider.repository;

import com.aiprovider.mapper.AssetMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class AssetRepository {
    private final AssetMapper mapper;
    public AssetRepository(AssetMapper mapper) { this.mapper = mapper; }
    public int upsertBatch(String platform, List<Map<String,Object>> rows) { return mapper.upsertBatch(platform, rows); }
    public List<Map<String,Object>> findPage(String platform, String status, int limit, int offset) { return mapper.findPage(platform, status, limit, offset); }
    public List<Map<String,Object>> findByPathHashes(String platform, List<String> pathHashes) { return mapper.findByPathHashes(platform, pathHashes); }
    public long count(String platform, String status) { return mapper.count(platform, status); }
    public List<Map<String,Object>> findImagePromptPool(String platform) { return mapper.findImagePromptPool(platform); }
    public Map<String,Object> findById(long id) { return mapper.findById(id); }
    public List<Long> findExistingIds(List<Long> ids) { return mapper.findExistingIds(ids); }
    public int deleteByIds(String platform, List<Long> ids) { return mapper.deleteByIds(platform, ids); }
    public int trashByIds(String platform, List<Long> ids) { return mapper.trashByIds(platform, ids); }
    public int restoreByIds(String platform, List<Long> ids) { return mapper.restoreByIds(platform, ids); }
    public int updateStatus(String platform, List<Long> ids, String status) { return mapper.updateStatus(platform, ids, status); }
}
