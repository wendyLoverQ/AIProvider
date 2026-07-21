package com.aiprovider.service;

import com.aiprovider.repository.PromptTranslationCacheRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptTranslationCacheServiceTest {
    @Test void storesOnlyTheHashAndReturnsAHitAfterRecordingIt() {
        PromptTranslationCacheRepository repository = mock(PromptTranslationCacheRepository.class);
        PromptTranslationCacheService service = new PromptTranslationCacheService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.anyString(), eq(12), eq("zh"), eq("libretranslate-v1"), eq("中文译文"))).thenReturn(1);

        assertThat(service.save("English text", "中文译文")).isEqualTo(1);

        verify(repository).save(eq("7eb3e0f4feac53df5237cbf2cc88c73fcbefa89f4d0b70f2552314b36827535c"), eq(12), eq("zh"), eq("libretranslate-v1"), eq("中文译文"));
    }

    @Test void failsWhenAHitCannotBeRecorded() {
        PromptTranslationCacheRepository repository = mock(PromptTranslationCacheRepository.class);
        PromptTranslationCacheService service = new PromptTranslationCacheService(repository);
        when(repository.find(org.mockito.ArgumentMatchers.anyString(), eq(12), eq("zh"), eq("libretranslate-v1")))
                .thenReturn(new PromptTranslationCacheRepository.CacheEntry(9L, "中文译文"));
        when(repository.recordHit(9L)).thenReturn(0);

        assertThatThrownBy(() -> service.find("English text"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("命中计数");
    }
}
