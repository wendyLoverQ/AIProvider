package com.aiprovider.repository;

import com.aiprovider.mapper.PromptCatalogMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class PromptCatalogRepository {
    private final PromptCatalogMapper mapper;
    public PromptCatalogRepository(PromptCatalogMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> findEnabledOptions() { return mapper.findEnabledOptions(); }
    public List<Map<String, Object>> findEnabledNegativeOptions() { return mapper.findEnabledNegativeOptions(); }
    public List<Map<String, Object>> findOptionPage(String query, String category, Boolean enabled, int limit, long offset) { return mapper.findOptionPage(query, category, enabled, limit, offset); }
    public long countOptions(String query, String category, Boolean enabled) { return mapper.countOptions(query, category, enabled); }
    public String findGeneralNegativePrompt() { return mapper.findGeneralNegativePrompt(); }
    public boolean existsOption(String id) { return mapper.countOption(id) > 0; }
    public void insertOption(PromptCatalogMapper.OptionRecord option) { mapper.insertOption(option); }
    public boolean updateOption(PromptCatalogMapper.OptionRecord option) { return mapper.updateOption(option) > 0; }
    public int countSchemeReferences(String id) { return mapper.countSchemeReferences(id); }
    public boolean deleteOption(String id) { return mapper.deleteOption(id) > 0; }
}
