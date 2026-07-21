package com.aiprovider.repository;

import com.aiprovider.mapper.PromptTranslationCacheMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class PromptTranslationCacheRepository {
    private final PromptTranslationCacheMapper mapper;

    public PromptTranslationCacheRepository(PromptTranslationCacheMapper mapper) { this.mapper = mapper; }

    public CacheEntry find(String sourceSha256, int sourceLength, String targetLanguage, String provider) {
        Map<String, Object> row = mapper.find(sourceSha256, sourceLength, targetLanguage, provider);
        if (row == null) return null;
        return new CacheEntry(((Number) row.get("id")).longValue(), String.valueOf(row.get("translatedText")));
    }

    public int recordHit(long id) { return mapper.recordHit(id); }

    public int save(String sourceSha256, int sourceLength, String targetLanguage, String provider, String translatedText) {
        return mapper.save(sourceSha256, sourceLength, targetLanguage, provider, translatedText);
    }

    public static final class CacheEntry {
        private final long id;
        private final String translatedText;

        public CacheEntry(long id, String translatedText) { this.id = id; this.translatedText = translatedText; }
        public long getId() { return id; }
        public String getTranslatedText() { return translatedText; }
    }
}
