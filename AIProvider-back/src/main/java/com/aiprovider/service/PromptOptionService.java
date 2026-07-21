package com.aiprovider.service;

import com.aiprovider.mapper.PromptCatalogMapper;
import com.aiprovider.model.dto.PromptOptionDTO;
import com.aiprovider.model.vo.PromptOptionVO;
import com.aiprovider.model.vo.PromptOptionPageVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PromptOptionService {
    private static final Logger log = LoggerFactory.getLogger(PromptOptionService.class);
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_]{0,63}");
    private static final Set<String> MULTIPLE_CATEGORIES = Set.of("Character", "Appearance", "Special", "Clothing", "Artist", "Relationship", "Action", "Composition", "Eyes", "Hair", "Background", "Lighting", "Style", "Quality");
    private static final Set<String> CATEGORIES = Set.of("Action", "Appearance", "Artist", "Background", "Camera", "Character", "Clothing", "Composition", "Expression", "Eyes", "Hair", "Lighting", "Pose", "Quality", "Relationship", "Special", "Style");
    private final PromptCatalogRepository repository;
    private final PromptTranslationService translationService;

    @Autowired
    public PromptOptionService(PromptCatalogRepository repository, PromptTranslationService translationService) {
        this.repository = repository;
        this.translationService = translationService;
    }
    public PromptOptionPageVO page(String query, String category, String status, String type, int page, int pageSize) {
        if (page < 1) throw new IllegalArgumentException("page 必须大于等于 1");
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        String normalizedQuery = optional(query, "搜索词", 100);
        String normalizedCategory = optional(category, "分类", 32);
        if (normalizedCategory != null && !CATEGORIES.contains(normalizedCategory)) throw new IllegalArgumentException("词条分类无效");
        String normalizedStatus = status == null ? "all" : status.trim().toLowerCase(Locale.ROOT);
        Boolean enabled;
        if ("all".equals(normalizedStatus)) enabled = null;
        else if ("enabled".equals(normalizedStatus)) enabled = true;
        else if ("disabled".equals(normalizedStatus)) enabled = false;
        else throw new IllegalArgumentException("status 只能是 all、enabled 或 disabled");
        String normalizedType = optional(type, "词条类型", 16);
        if (normalizedType != null) normalizedType = normalizedType.toLowerCase(Locale.ROOT);
        if (normalizedType != null && !normalizedType.equals("positive") && !normalizedType.equals("negative"))
            throw new IllegalArgumentException("type 只能是 positive 或 negative");
        List<PromptOptionVO> result = new ArrayList<>();
        long offset = (long) (page - 1) * pageSize;
        for (Map<String, Object> row : repository.findOptionPage(normalizedQuery, normalizedCategory, enabled, normalizedType, pageSize, offset)) result.add(toVO(row));
        return new PromptOptionPageVO(result, repository.countOptions(normalizedQuery, normalizedCategory, enabled, normalizedType), page, pageSize);
    }

    public List<PromptOptionVO> resolve(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        if (ids.size() > 500) throw new IllegalArgumentException("一次最多解析 500 个词条");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) { validateId(id); normalized.add(id); }
        Map<String, PromptOptionVO> byId = new HashMap<>();
        for (Map<String, Object> row : repository.findOptionsByIds(new ArrayList<>(normalized))) {
            PromptOptionVO option = toVO(row); byId.put(option.getId(), option);
        }
        List<PromptOptionVO> result = new ArrayList<>();
        for (String id : normalized) if (byId.containsKey(id)) result.add(byId.get(id));
        return result;
    }

    public List<PromptOptionVO> analyze(String positivePrompt, String negativePrompt) {
        long started = System.nanoTime();
        List<String> positiveTerms = promptTerms(positivePrompt, "正向 Prompt");
        List<String> negativeTerms = promptTerms(negativePrompt, "反向 Prompt");
        LinkedHashMap<String, PromptOptionVO> matches = new LinkedHashMap<>();
        if (!positiveTerms.isEmpty()) {
            for (Map<String, Object> row : repository.findEnabledOptionsByTerms(positiveTerms, "positive")) {
                PromptOptionVO option = toVO(row); matches.put(option.getId(), option);
            }
        }
        if (!negativeTerms.isEmpty()) {
            for (Map<String, Object> row : repository.findEnabledOptionsByTerms(negativeTerms, "negative")) {
                PromptOptionVO option = toVO(row); matches.put(option.getId(), option);
            }
        }
        log.info("prompt_options_analyzed positiveTerms={} negativeTerms={} matches={} elapsedMs={}",
                positiveTerms.size(), negativeTerms.size(), matches.size(), (System.nanoTime() - started) / 1_000_000L);
        return new ArrayList<>(matches.values());
    }

    public Map<String, String> config() {
        String generalNegativePrompt = repository.findGeneralNegativePrompt();
        if (generalNegativePrompt == null || generalNegativePrompt.trim().isEmpty())
            throw new IllegalStateException("通用反向模板未配置或未启用");
        return Collections.singletonMap("generalNegativePrompt", generalNegativePrompt.trim());
    }

    @Transactional
    public void create(PromptOptionDTO dto) {
        PromptCatalogMapper.OptionRecord record = validate(dto);
        if (repository.existsOption(record.getId())) throw new IllegalArgumentException("词条 ID 已存在");
        repository.insertOption(record);
        translationService.invalidate();
    }

    @Transactional
    public void update(String id, PromptOptionDTO dto) {
        PromptCatalogMapper.OptionRecord record = validate(dto);
        if (!record.getId().equals(id)) throw new IllegalArgumentException("词条 ID 创建后不能修改");
        if (!repository.updateOption(record)) throw new IllegalArgumentException("词条不存在");
        translationService.invalidate();
    }

    @Transactional
    public void delete(String id) {
        validateId(id);
        if (repository.countSchemeReferences(id) > 0) throw new IllegalArgumentException("词条正在被 Prompt 方案使用，请先从方案中移除该词条");
        if (!repository.deleteOption(id)) throw new IllegalArgumentException("词条不存在");
        translationService.invalidate();
    }

    private PromptCatalogMapper.OptionRecord validate(PromptOptionDTO dto) {
        if (dto == null) throw new IllegalArgumentException("词条不能为空");
        validateId(dto.getId());
        if (!CATEGORIES.contains(dto.getCategory())) throw new IllegalArgumentException("词条分类无效");
        String name = required(dto.getName(), "中文名称", 100);
        String type = required(dto.getType(), "词条类型", 16).toLowerCase(Locale.ROOT);
        if (!type.equals("positive") && !type.equals("negative")) throw new IllegalArgumentException("词条类型只能是 positive 或 negative");
        String prompt = required(dto.getPrompt(), "Prompt 词", 500);
        String reverseId = optional(dto.getReverseId(), "反向词条 ID", 64);
        if (type.equals("negative") && reverseId != null) throw new IllegalArgumentException("反向词条不能再关联反向词条");
        if (dto.getSortOrder() == null || dto.getSortOrder() < 0 || dto.getSortOrder() > 100000) throw new IllegalArgumentException("排序必须在 0-100000 之间");
        if (dto.getEnabled() == null || dto.getAllowMultiple() == null) throw new IllegalArgumentException("启用状态和多选规则不能为空");
        boolean expectedMultiple = type.equals("positive") && MULTIPLE_CATEGORIES.contains(dto.getCategory());
        if (dto.getAllowMultiple() != expectedMultiple) throw new IllegalArgumentException("该分类的多选规则应为：" + expectedMultiple);
        PromptCatalogMapper.OptionRecord record = new PromptCatalogMapper.OptionRecord();
        record.setId(dto.getId()); record.setCategory(dto.getCategory()); record.setName(name);
        record.setPrompt(prompt); record.setType(type); record.setReverseId(reverseId); record.setSortOrder(dto.getSortOrder());
        record.setEnabled(dto.getEnabled()); record.setAllowMultiple(expectedMultiple); return record;
    }

    private List<String> promptTerms(String value, String label) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        if (value.length() > 16000) throw new IllegalArgumentException(label + "不能超过 16000 字符");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String term = part.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!term.isEmpty()) terms.add(term);
        }
        if (terms.size() > 1000) throw new IllegalArgumentException(label + "一次最多解析 1000 个词");
        return new ArrayList<>(terms);
    }

    private PromptOptionVO toVO(Map<String, Object> row) {
        return new PromptOptionVO(text(row.get("id")), text(row.get("category")), text(row.get("name")), text(row.get("prompt")), text(row.get("type")), text(row.get("reverseId")),
                text(row.get("positivePrompt")), text(row.get("negativePrompt")), integer(row.get("sortOrder")), truth(row.get("enabled")), truth(row.get("allowMultiple")));
    }
    private void validateId(String id) { if (id == null || !ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("词条 ID 只能使用小写字母、数字和下划线，长度 1-64"); }
    private String required(String value, String label, int max) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + "不能为空"); return optional(value, label, max); }
    private String optional(String value, String label, int max) { if (value == null || value.trim().isEmpty()) return null; String result = value.trim(); if (result.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 字符"); return result; }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private int integer(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value)); }
    private boolean truth(Object value) { return value instanceof Boolean ? (Boolean) value : value instanceof Number ? ((Number) value).intValue() != 0 : Boolean.parseBoolean(String.valueOf(value)); }
}
