package com.aiprovider.repository;

import com.aiprovider.mapper.LocalGeneratedImageMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class LocalGeneratedImageRepository {
    private final LocalGeneratedImageMapper mapper;
    public LocalGeneratedImageRepository(LocalGeneratedImageMapper mapper) { this.mapper = mapper; }
    public int upsertBatch(String platform, List<Map<String,Object>> rows) { return mapper.upsertBatch(platform, rows); }
    public List<Map<String,Object>> findPage(String platform, String status, int limit, int offset) { return mapper.findPage(platform, status, limit, offset); }
    public long count(String platform, String status) { return mapper.count(platform, status); }
    public int trash(String platform, List<String> hashes) { return mapper.trash(platform, hashes); }
    public int restore(String platform, List<String> hashes) { return mapper.restore(platform, hashes); }
    public int delete(String platform, List<String> hashes) { return mapper.delete(platform, hashes); }
}
