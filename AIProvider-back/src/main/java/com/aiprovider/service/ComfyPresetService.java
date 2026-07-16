package com.aiprovider.service;

import com.aiprovider.mapper.ComfyPresetMapper;
import com.aiprovider.model.dto.ComfyPresetDTO;
import com.aiprovider.model.vo.ComfyPresetVO;
import com.aiprovider.repository.ComfyPresetRepository;
import com.aiprovider.repository.PromptCatalogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ComfyPresetService {
    private final ComfyPresetRepository presets;
    private final PromptCatalogRepository catalog;
    private final ObjectMapper json;

    public ComfyPresetService(ComfyPresetRepository presets, PromptCatalogRepository catalog, ObjectMapper json) {
        this.presets = presets; this.catalog = catalog; this.json = json;
    }

    public List<ComfyPresetVO> list() {
        List<ComfyPresetVO> result = new ArrayList<>();
        for (Map<String, Object> row : presets.findAll()) {
            result.add(new ComfyPresetVO(number(row.get("id")), text(row.get("name")), parseSelections(text(row.get("selectedOptionsJson"))),
                    textOrEmpty(row.get("positiveExtra")), textOrEmpty(row.get("negativeExtra")), textOrEmpty(row.get("positivePrompt")),
                    textOrEmpty(row.get("negativePrompt")), text(row.get("remark")), truth(row.get("isDefault"))));
        }
        return result;
    }

    @Transactional
    public long create(ComfyPresetDTO dto) {
        validate(dto);
        if (Boolean.TRUE.equals(dto.getIsDefault())) presets.clearDefault();
        return presets.insert(record(dto));
    }

    @Transactional
    public void update(long id, ComfyPresetDTO dto) {
        validate(dto);
        if (Boolean.TRUE.equals(dto.getIsDefault())) presets.clearDefault();
        ComfyPresetMapper.PresetRecord record = record(dto); record.setId(id);
        if (!presets.update(record)) throw new IllegalArgumentException("Prompt 方案不存在");
    }

    @Transactional
    public void setDefault(long id) {
        presets.clearDefault();
        if (!presets.setDefault(id)) throw new IllegalArgumentException("Prompt 方案不存在");
    }

    @Transactional
    public void clearDefault() { presets.clearDefault(); }

    @Transactional
    public void delete(long id) {
        if (!presets.delete(id)) throw new IllegalArgumentException("Prompt 方案不存在");
    }

    private ComfyPresetMapper.PresetRecord record(ComfyPresetDTO dto) {
        ComfyPresetMapper.PresetRecord record = new ComfyPresetMapper.PresetRecord();
        record.setName(dto.getName().trim());
        try { record.setSelectedOptionsJson(json.writeValueAsString(dto.getSelectedOptions())); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("结构化选择不是有效 JSON", e); }
        record.setPositiveExtra(cleanPrompt(dto.getPositiveExtra())); record.setNegativeExtra(cleanPrompt(dto.getNegativeExtra()));
        record.setPositivePrompt(cleanPrompt(dto.getPositivePrompt())); record.setNegativePrompt(cleanPrompt(dto.getNegativePrompt()));
        record.setRemark(cleanNullable(dto.getRemark())); record.setDefault(Boolean.TRUE.equals(dto.getIsDefault()));
        return record;
    }

    private void validate(ComfyPresetDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Prompt 方案不能为空");
        if (dto.getName() == null || dto.getName().trim().isEmpty() || dto.getName().trim().length() > 100)
            throw new IllegalArgumentException("方案名称长度应为 1-100");
        if (dto.getRemark() != null && dto.getRemark().trim().length() > 1000) throw new IllegalArgumentException("备注不能超过 1000 字");
        Map<String, String> optionCategories = new HashMap<>();
        Map<String, Boolean> categoryMultiple = new LinkedHashMap<>();
        for (Map<String, Object> row : catalog.findEnabledOptions()) {
            String category = text(row.get("category"));
            optionCategories.put(text(row.get("id")), category);
            categoryMultiple.merge(category, truth(row.get("allowMultiple")), (left, right) -> left || right);
        }
        if (categoryMultiple.isEmpty()) throw new IllegalStateException("Prompt 词条分类为空");
        if (dto.getSelectedOptions() == null || !dto.getSelectedOptions().keySet().equals(categoryMultiple.keySet()))
            throw new IllegalArgumentException("结构化选择必须与当前 Prompt 词条分类完全一致");

        Set<String> selectedIds = new HashSet<>();
        for (Map.Entry<String, Boolean> definition : categoryMultiple.entrySet()) {
            List<String> ids = dto.getSelectedOptions().get(definition.getKey());
            if (ids == null) throw new IllegalArgumentException("分类选择不能为 null：" + definition.getKey());
            if (!definition.getValue() && ids.size() > 1) throw new IllegalArgumentException("单选分类只能选择一项：" + definition.getKey());
            for (String id : ids) {
                if (id == null || id.trim().isEmpty() || !selectedIds.add(id)) throw new IllegalArgumentException("词条选择包含空值或重复项");
                if (!definition.getKey().equals(optionCategories.get(id))) throw new IllegalArgumentException("词条不存在、未启用或分类不匹配：" + id);
            }
        }
        validatePromptLength(dto.getPositiveExtra(), "正向手动补充", 8000); validatePromptLength(dto.getNegativeExtra(), "反向手动补充", 8000);
        validatePromptLength(dto.getPositivePrompt(), "最终正向 Prompt", 16000); validatePromptLength(dto.getNegativePrompt(), "最终反向 Prompt", 16000);
    }

    private void validatePromptLength(String value, String label, int max) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        if (value.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 字符");
    }
    private String cleanPrompt(String value) { return value.trim(); }
    private String cleanNullable(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private Map<String, List<String>> parseSelections(String value) {
        try { return json.readValue(value, new TypeReference<Map<String, List<String>>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("数据库中的结构化选择 JSON 无效", e); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private String textOrEmpty(Object value) { return value == null ? "" : String.valueOf(value); }
    private Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private boolean truth(Object value) { return value instanceof Boolean ? (Boolean) value : value instanceof Number ? ((Number) value).intValue() != 0 : Boolean.parseBoolean(String.valueOf(value)); }
}
