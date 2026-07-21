package com.aiprovider.service;

import com.aiprovider.repository.PromptTranslationCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PromptTranslationCacheService {
    private static final Logger log = LoggerFactory.getLogger(PromptTranslationCacheService.class);
    static final String TARGET_LANGUAGE = "zh";
    static final String PROVIDER = "libretranslate-v1";
    private final PromptTranslationCacheRepository repository;

    public PromptTranslationCacheService(PromptTranslationCacheRepository repository) { this.repository = repository; }

    public String find(String sourceText) {
        if (sourceText == null || sourceText.isEmpty()) return null;
        PromptTranslationCacheRepository.CacheEntry entry = repository.find(hash(sourceText), sourceText.length(), TARGET_LANGUAGE, PROVIDER);
        if (entry == null) return null;
        int affectedRows = repository.recordHit(entry.getId());
        if (affectedRows != 1) {
            log.warn("prompt_translation_cache_hit_mismatch operation=recordHit cacheId={} requestedCount=1 affectedRows={}", entry.getId(), affectedRows);
            throw new IllegalStateException("翻译缓存命中计数更新失败");
        }
        return entry.getTranslatedText();
    }

    public int save(String sourceText, String translatedText) {
        if (sourceText == null || sourceText.isEmpty() || translatedText == null || translatedText.isEmpty())
            throw new IllegalArgumentException("翻译缓存内容不能为空");
        int affectedRows = repository.save(hash(sourceText), sourceText.length(), TARGET_LANGUAGE, PROVIDER, translatedText);
        if (affectedRows < 1) {
            log.warn("prompt_translation_cache_save_mismatch operation=save targetLanguage={} provider={} requestedCount=1 affectedRows={}",
                    TARGET_LANGUAGE, PROVIDER, affectedRows);
            throw new IllegalStateException("翻译缓存写入失败");
        }
        return affectedRows;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
