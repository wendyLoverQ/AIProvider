package com.aiprovider.service;

import com.aiprovider.model.vo.PromptCatalogVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptCatalogServiceTest {
    @Test void returnsConfiguredOptionsAndTemplateWithoutFallback() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class); PromptCatalogService service = new PromptCatalogService(repository);
        Map<String, Object> first = row(1, true); Map<String, Object> second = row("2", 1); second.put("allowMultiple", "true");
        when(repository.findGeneralNegativePrompt()).thenReturn(" negative "); when(repository.findEnabledOptions()).thenReturn(Arrays.asList(first, second));
        PromptCatalogVO result = service.get();
        assertThat(result.getGeneralNegativePrompt()).isEqualTo("negative"); assertThat(result.getOptions()).hasSize(2);
        assertThat(result.getOptions().get(0).getId()).isEqualTo("option"); assertThat(result.getOptions()).allMatch(option -> option.isAllowMultiple());
        when(repository.findGeneralNegativePrompt()).thenReturn(null);
        assertThatThrownBy(service::get).isInstanceOf(IllegalStateException.class).hasMessageContaining("未配置");
        when(repository.findGeneralNegativePrompt()).thenReturn("  ");
        assertThatThrownBy(service::get).isInstanceOf(IllegalStateException.class).hasMessageContaining("未配置");
    }
    private Map<String, Object> row(Object sortOrder, Object multiple) {
        Map<String, Object> row = new HashMap<>(); row.put("id", "option"); row.put("category", "quality"); row.put("name", "画质");
        row.put("positivePrompt", "best quality"); row.put("negativePrompt", null); row.put("sortOrder", sortOrder); row.put("allowMultiple", multiple); return row;
    }
}
