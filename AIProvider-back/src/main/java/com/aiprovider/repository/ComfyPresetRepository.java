package com.aiprovider.repository;

import com.aiprovider.mapper.ComfyPresetMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ComfyPresetRepository {
    private final ComfyPresetMapper mapper;
    public ComfyPresetRepository(ComfyPresetMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> findAll() { return mapper.findAll(); }
    public long insert(ComfyPresetMapper.PresetRecord preset) { mapper.insert(preset); return preset.getId(); }
    public boolean update(ComfyPresetMapper.PresetRecord preset) { return mapper.update(preset) > 0; }
    public void clearDefault() { mapper.clearDefault(); }
    public boolean setDefault(long id) { return mapper.setDefault(id) > 0; }
    public boolean delete(long id) { return mapper.delete(id) > 0; }
}
