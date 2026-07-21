package com.aiprovider.service;

import com.aiprovider.model.dto.PromptTranslationDTO;
import com.aiprovider.model.vo.PromptTranslationVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptTranslationServiceTest {
    @Test void keepsTheOriginalEnglishTermAfterEveryPositiveChineseLabel() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("name", "黑色连裤袜");
        option.put("positivePrompt", "black pantyhose");
        option.put("negativePrompt", "bare legs, white pantyhose");
        when(repository.findEnabledOptions()).thenReturn(Collections.singletonList(option));
        when(repository.findEnabledNegativeOptions()).thenReturn(Collections.emptyList());
        LibreTranslateService proseTranslation = mock(LibreTranslateService.class);
        PromptTranslationDTO request = new PromptTranslationDTO();
        request.setPositivePrompt("black pantyhose, unmapped term");
        request.setNegativePrompt("bare legs");

        PromptTranslationVO result = new PromptTranslationService(repository, proseTranslation).translate(request);

        assertThat(result.getPositivePrompt()).isEqualTo("黑色连裤袜（black pantyhose）, unmapped term");
        assertThat(result.getNegativePrompt()).isEqualTo("黑色连裤袜（bare legs）");
    }

    @Test void imageInfoTranslationUsesTheSamePersistentProseCache() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class);
        when(repository.findEnabledOptions()).thenReturn(Collections.emptyList());
        when(repository.findEnabledNegativeOptions()).thenReturn(Collections.emptyList());
        LibreTranslateService proseTranslation = mock(LibreTranslateService.class);
        when(proseTranslation.findCached("A long scene description.")).thenReturn("一段长场景描述。");
        PromptTranslationDTO request = new PromptTranslationDTO();
        request.setPositivePrompt("A long scene description.");
        request.setNegativePrompt("");

        PromptTranslationVO result = new PromptTranslationService(repository, proseTranslation).translate(request);

        assertThat(result.getPositivePrompt()).isEqualTo("一段长场景描述。");
        assertThat(result.getNegativePrompt()).isEmpty();
    }
}
