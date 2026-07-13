package com.aiprovider.service;

import com.aiprovider.mapper.ComfyPresetMapper;
import com.aiprovider.model.dto.ComfyPresetDTO;
import com.aiprovider.model.vo.ComfyPresetVO;
import com.aiprovider.repository.ComfyPresetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ComfyPresetService {
    private final ComfyPresetRepository presets;
    private final ObjectMapper json;

    public ComfyPresetService(ComfyPresetRepository presets, ObjectMapper json) {
        this.presets = presets; this.json = json;
    }

    public List<ComfyPresetVO> list() {
        List<ComfyPresetVO> result = new ArrayList<>();
        for (Map<String, Object> row : presets.findAll()) {
            result.add(new ComfyPresetVO(number(row.get("id")), text(row.get("title")),
                    text(row.get("outputFolder")), parse(text(row.get("parametersJson"))), text(row.get("notes")), truth(row.get("isDefault"))));
        }
        return result;
    }

    @Transactional
    public long create(ComfyPresetDTO dto) {
        validate(dto);
        return presets.insert(record(dto));
    }

    @Transactional
    public void update(long id, ComfyPresetDTO dto) {
        validate(dto);
        ComfyPresetMapper.PresetInsert record = record(dto);
        record.setId(id);
        if (!presets.update(record)) throw new IllegalArgumentException("参数方案不存在");
    }

    @Transactional
    public void setDefault(long id) {
        presets.clearDefault();
        if (!presets.setDefault(id)) throw new IllegalArgumentException("参数方案不存在");
    }

    private ComfyPresetMapper.PresetInsert record(ComfyPresetDTO dto) {
        ComfyPresetMapper.PresetInsert record = new ComfyPresetMapper.PresetInsert();
        record.setTitle(dto.getTitle().trim());
        record.setOutputFolder(normalizeFolder(dto.getOutputFolder()));
        record.setNotes(dto.getNotes() == null || dto.getNotes().trim().isEmpty() ? null : dto.getNotes().trim());
        Map<String, Object> parameters = new LinkedHashMap<>(dto.getParameters());
        parameters.keySet().retainAll(Arrays.asList("positivePrompt", "negativePrompt"));
        try { record.setParametersJson(json.writeValueAsString(parameters)); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("参数方案不是有效 JSON", e); }
        return record;
    }

    @Transactional
    public void delete(long id) {
        if (!presets.delete(id)) throw new IllegalArgumentException("参数方案不存在");
    }

    private void validate(ComfyPresetDTO dto) {
        if (dto == null) throw new IllegalArgumentException("参数方案不能为空");
        String title = dto.getTitle();
        if (title == null || title.trim().isEmpty() || title.trim().length() > 100)
            throw new IllegalArgumentException("标题长度应为 1-100");
        if (dto.getParameters() == null)
            throw new IllegalArgumentException("参数不能为空");
        if (dto.getNotes() != null && dto.getNotes().trim().length() > 1000)
            throw new IllegalArgumentException("备注不能超过 1000 字");
        String folder = normalizeFolder(dto.getOutputFolder());
        if (folder.length() > 240 || folder.contains("..") || folder.startsWith("/") || folder.startsWith("\\"))
            throw new IllegalArgumentException("输出文件夹不合法");
    }

    private String normalizeFolder(String folder) { return folder == null || folder.trim().isEmpty() ? "aimaid" : folder.trim(); }
    private Map<String, Object> parse(String value) {
        try { return json.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("数据库中的参数方案 JSON 无效", e); }
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
    private boolean truth(Object value) { return value instanceof Boolean ? (Boolean) value : value instanceof Number ? ((Number) value).intValue() != 0 : Boolean.parseBoolean(String.valueOf(value)); }
}
