package com.aiprovider.repository;

import com.aiprovider.mapper.GalleryRecycleBinMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class GalleryRecycleBinRepository {
    private final GalleryRecycleBinMapper mapper;
    public GalleryRecycleBinRepository(GalleryRecycleBinMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> findPage(String platform, int limit, int offset) { return mapper.findPage(platform, limit, offset); }
    public long count(String platform) { return mapper.count(platform); }
}
