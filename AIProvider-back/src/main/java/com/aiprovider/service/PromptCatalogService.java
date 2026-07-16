package com.aiprovider.service;

import com.aiprovider.model.vo.PromptCatalogVO;
import com.aiprovider.model.vo.PromptOptionVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PromptCatalogService {
    private final PromptCatalogRepository repository;
    public PromptCatalogService(PromptCatalogRepository repository) { this.repository = repository; }
    public PromptCatalogVO get() {
        String template = repository.findGeneralNegativePrompt();
        if (template == null || template.trim().isEmpty()) throw new IllegalStateException("通用反向模板未配置或未启用");
        List<PromptOptionVO> options = new ArrayList<>();
        for (Map<String, Object> row : repository.findEnabledOptions()) {
            options.add(new PromptOptionVO(text(row.get("id")), text(row.get("category")), text(row.get("name")), text(row.get("prompt")), text(row.get("type")), text(row.get("reverseId")),
                    text(row.get("positivePrompt")), text(row.get("negativePrompt")), integer(row.get("sortOrder")), true, truth(row.get("allowMultiple"))));
        }
        List<PromptOptionVO> negativeOptions = new ArrayList<>();
        for (Map<String, Object> row : repository.findEnabledNegativeOptions()) {
            negativeOptions.add(new PromptOptionVO(text(row.get("id")), text(row.get("category")), text(row.get("name")), text(row.get("prompt")), text(row.get("type")), null,
                    null, text(row.get("negativePrompt")), integer(row.get("sortOrder")), true, false));
        }
        return new PromptCatalogVO(options, negativeOptions, template.trim());
    }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private int integer(Object value) { return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value)); }
    private boolean truth(Object value) { return value instanceof Boolean ? (Boolean) value : value instanceof Number ? ((Number) value).intValue() != 0 : Boolean.parseBoolean(String.valueOf(value)); }
}
