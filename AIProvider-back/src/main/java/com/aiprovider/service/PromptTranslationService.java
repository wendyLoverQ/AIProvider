package com.aiprovider.service;

import com.aiprovider.model.dto.PromptTranslationDTO;
import com.aiprovider.model.vo.PromptTranslationVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptTranslationService {
    private final PromptCatalogRepository repository;
    private final LibreTranslateService proseTranslation;
    private volatile TranslationCatalog catalog;

    public PromptTranslationService(PromptCatalogRepository repository, LibreTranslateService proseTranslation) {
        this.repository = repository;
        this.proseTranslation = proseTranslation;
    }

    public PromptTranslationVO translate(PromptTranslationDTO dto) {
        if (dto == null) throw new IllegalArgumentException("Prompt 不能为空");
        String positive = validate(dto.getPositivePrompt(), "正向 Prompt");
        String negative = validate(dto.getNegativePrompt(), "反向 Prompt");
        TranslationCatalog current = catalog();
        String cachedPositive = proseTranslation.findCached(positive);
        String cachedNegative = proseTranslation.findCached(negative);
        return new PromptTranslationVO(
                cachedPositive == null ? translate(positive, current.positive) : cachedPositive,
                cachedNegative == null ? translate(negative, current.negative) : cachedNegative);
    }

    public void invalidate() { catalog = null; }

    private TranslationCatalog catalog() {
        TranslationCatalog current = catalog;
        if (current != null) return current;
        synchronized (this) {
            if (catalog != null) return catalog;
            Map<String, String> positive = new LinkedHashMap<>();
            Map<String, String> negative = new LinkedHashMap<>();
            for (Map<String, Object> row : repository.findEnabledOptions()) {
                addTerms(positive, text(row.get("positivePrompt")), text(row.get("name")), true);
                addTerms(negative, text(row.get("negativePrompt")), text(row.get("name")), false);
            }
            for (Map<String, Object> row : repository.findEnabledNegativeOptions())
                addTerms(negative, text(row.get("negativePrompt")), text(row.get("name")), false);
            catalog = new TranslationCatalog(Map.copyOf(positive), Map.copyOf(negative));
            return catalog;
        }
    }

    private void addTerms(Map<String, String> target, String prompt, String name, boolean alwaysShowOriginal) {
        if (prompt == null || name == null || name.isBlank()) return;
        String[] terms = prompt.split(",");
        for (String term : terms) {
            String clean = normalizeTerm(term);
            if (clean.isEmpty()) continue;
            String label = alwaysShowOriginal || terms.length > 1 ? name.trim() + "（" + clean + "）" : name.trim();
            target.putIfAbsent(clean.toLowerCase(Locale.US), label);
        }
    }

    private String translate(String prompt, Map<String, String> translations) {
        if (prompt.isEmpty()) return "";
        return java.util.Arrays.stream(prompt.split(","))
                .map(this::normalizeTerm)
                .filter(term -> !term.isEmpty())
                .map(term -> translations.getOrDefault(term.toLowerCase(Locale.US), term))
                .collect(Collectors.joining(", "));
    }

    private String validate(String value, String label) {
        if (value == null || value.isBlank()) return "";
        String result = value.trim();
        if (result.length() > 20000) throw new IllegalArgumentException(label + "不能超过 20000 字符");
        return result;
    }

    private String normalizeTerm(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static final class TranslationCatalog {
        private final Map<String, String> positive;
        private final Map<String, String> negative;
        private TranslationCatalog(Map<String, String> positive, Map<String, String> negative) {
            this.positive = positive;
            this.negative = negative;
        }
    }
}
