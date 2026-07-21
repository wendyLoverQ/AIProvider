package com.aiprovider.service;

import com.aiprovider.mapper.PromptCatalogMapper;
import com.aiprovider.model.dto.PromptOptionDTO;
import com.aiprovider.model.vo.PromptOptionVO;
import com.aiprovider.repository.PromptCatalogRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromptOptionServiceTest {
    @Test void listsCreatesUpdatesAndDeletesValidatedOptions() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class); PromptOptionService service = service(repository);
        Map<String,Object> row = new HashMap<>(); row.put("id","black_stockings"); row.put("category","Clothing"); row.put("name","黑丝袜"); row.put("prompt","black stockings"); row.put("type","positive"); row.put("reverseId",null); row.put("sortOrder",1); row.put("enabled",1); row.put("allowMultiple",true);
        when(repository.findOptionPage(null, null, null, null, 100, 0)).thenReturn(Collections.singletonList(row));
        when(repository.countOptions(null, null, null, null)).thenReturn(1L);
        assertThat(service.page(null, null, "all", null, 1, 100).getItems()).singleElement().satisfies(item -> { assertThat(item.getName()).isEqualTo("黑丝袜"); assertThat(item.isEnabled()).isTrue(); });
        when(repository.findOptionsByIds(Collections.singletonList("black_stockings"))).thenReturn(Collections.singletonList(row));
        assertThat(service.resolve(Arrays.asList("black_stockings", "black_stockings"))).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo("black_stockings"));
        when(repository.findEnabledOptionsByTerms(Arrays.asList("black stockings", "standing"), "positive")).thenReturn(Collections.singletonList(row));
        assertThat(service.analyze("black stockings, standing", "")).singleElement().satisfies(item -> assertThat(item.getId()).isEqualTo("black_stockings"));
        when(repository.findGeneralNegativePrompt()).thenReturn(" low quality ");
        assertThat(service.config()).containsEntry("generalNegativePrompt", "low quality");
        PromptOptionDTO dto = valid(); service.create(dto); verify(repository).insertOption(any(PromptCatalogMapper.OptionRecord.class));
        when(repository.updateOption(any())).thenReturn(true); service.update(dto.getId(), dto);
        when(repository.countSchemeReferences(dto.getId())).thenReturn(0); when(repository.deleteOption(dto.getId())).thenReturn(true); service.delete(dto.getId());
    }

    @Test void pagesAndFiltersOptionsOnTheServer() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class); PromptOptionService service = service(repository);
        when(repository.findOptionPage("丝袜", "Clothing", true, "positive", 50, 50)).thenReturn(Collections.emptyList());
        when(repository.countOptions("丝袜", "Clothing", true, "positive")).thenReturn(123L);
        assertThat(service.page(" 丝袜 ", "Clothing", "enabled", "positive", 2, 50).getPages()).isEqualTo(3);
        verify(repository).findOptionPage("丝袜", "Clothing", true, "positive", 50, 50);
        assertThatThrownBy(() -> service.page(null, null, "all", null, 1, 101)).hasMessageContaining("pageSize");
        assertThatThrownBy(() -> service.page(null, null, "unknown", null, 1, 20)).hasMessageContaining("status");
    }

    @Test void analyzesOnlySubmittedPromptTermsWithoutLoadingTheCatalog() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class); PromptOptionService service = service(repository);
        Map<String,Object> row = new HashMap<>(); row.put("id","standing"); row.put("category","Pose"); row.put("name","站立"); row.put("prompt","standing"); row.put("type","positive"); row.put("sortOrder",1); row.put("enabled",1); row.put("allowMultiple",false);
        when(repository.findEnabledOptionsByTerms(Arrays.asList("standing", "very tall woman"), "positive")).thenReturn(Collections.singletonList(row));
        assertThat(service.analyze("standing, very tall woman", "")).extracting(PromptOptionVO::getId).containsExactly("standing");
        verify(repository, never()).findEnabledOptions();
        verify(repository).findEnabledOptionsByTerms(Arrays.asList("standing", "very tall woman"), "positive");
    }

    @Test void rejectsDuplicatesInvalidDataReferencedAndMissingOptions() {
        PromptCatalogRepository repository = mock(PromptCatalogRepository.class); PromptOptionService service = service(repository); PromptOptionDTO dto = valid();
        when(repository.existsOption(dto.getId())).thenReturn(true); assertThatThrownBy(() -> service.create(dto)).hasMessageContaining("已存在");
        assertThatThrownBy(() -> service.update("another", dto)).hasMessageContaining("不能修改");
        when(repository.updateOption(any())).thenReturn(false); assertThatThrownBy(() -> service.update(dto.getId(), dto)).hasMessageContaining("不存在");
        when(repository.countSchemeReferences(dto.getId())).thenReturn(1); assertThatThrownBy(() -> service.delete(dto.getId())).hasMessageContaining("正在被");
        dto.setAllowMultiple(false); assertThatThrownBy(() -> service.create(dto)).hasMessageContaining("多选规则");
        assertThatThrownBy(() -> service.delete("Bad-ID")).hasMessageContaining("小写字母");
        assertThatThrownBy(() -> service.resolve(Collections.singletonList("Bad-ID"))).hasMessageContaining("小写字母");
        assertThatThrownBy(() -> service.resolve(Collections.nCopies(501, "valid_id"))).hasMessageContaining("500");
        when(repository.findGeneralNegativePrompt()).thenReturn(" "); assertThatThrownBy(service::config).hasMessageContaining("未配置");
    }

    private PromptOptionService service(PromptCatalogRepository repository) {
        return new PromptOptionService(repository, mock(PromptTranslationService.class));
    }

    private PromptOptionDTO valid() {
        PromptOptionDTO dto = new PromptOptionDTO(); dto.setId("black_stockings"); dto.setCategory("Clothing"); dto.setName(" 黑丝袜 ");
        dto.setPrompt(" black stockings "); dto.setType("positive"); dto.setReverseId(null); dto.setSortOrder(10); dto.setEnabled(true); dto.setAllowMultiple(true); return dto;
    }
}
