package com.aiprovider.repository;

import com.aiprovider.mapper.FavoriteMediaMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class FavoriteMediaRepository {
    private final FavoriteMediaMapper mapper;
    public FavoriteMediaRepository(FavoriteMediaMapper mapper) { this.mapper = mapper; }
    public int insert(FavoriteMediaMapper.Row row) { return mapper.insert(row); }
    public int insertBatch(List<FavoriteMediaMapper.Row> rows) { return mapper.insertBatch(rows); }
    public List<Map<String,Object>> findPage(int limit, int offset) { return mapper.findPage(limit, offset); }
    public long count() { return mapper.count(); }
    public Map<String,Object> findById(long id) { return mapper.findById(id); }
    public Map<String,Object> findBySha256(String sha256) { return mapper.findBySha256(sha256); }
    public List<String> findExistingSha256s(List<String> hashes) { return mapper.findExistingSha256s(hashes); }
    public int deleteByIds(List<Long> ids) { return mapper.deleteByIds(ids); }
}
