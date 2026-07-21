package com.aiprovider.service;

import com.aiprovider.model.dto.PromptTranslationDTO;
import com.aiprovider.model.vo.PromptTranslationVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibreTranslateServiceTest {
    @Test void translatesTheWholePositiveAndNegativeProseThroughTheConfiguredApi() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        PromptTranslationCacheService cache = mock(PromptTranslationCacheService.class);
        when(cache.save(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        server.expect(once(), requestTo("http://translate.local/translate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.source").value("auto"))
                .andExpect(jsonPath("$.target").value("zh"))
                .andExpect(jsonPath("$.api_key").value("secret-key"))
                .andExpect(jsonPath("$.q[0]").value("A detailed rainy street scene."))
                .andRespond(withSuccess("{\"translatedText\":[\"细致的雨夜街景。\",\"不要出现文字。\"]}", MediaType.APPLICATION_JSON));
        LibreTranslateService service = new LibreTranslateService("http://translate.local/translate", "secret-key", http, cache);
        PromptTranslationDTO dto = new PromptTranslationDTO();
        dto.setPositivePrompt("A detailed rainy street scene."); dto.setNegativePrompt("Do not include text.");

        PromptTranslationVO result = service.translateToChinese(dto);

        assertThat(result.getPositivePrompt()).isEqualTo("细致的雨夜街景。");
        assertThat(result.getNegativePrompt()).isEqualTo("不要出现文字。");
        verify(cache).save("A detailed rainy street scene.", "细致的雨夜街景。");
        verify(cache).save("Do not include text.", "不要出现文字。");
        server.verify();
    }

    @Test void returnsPersistentCacheWithoutCallingLibreTranslate() {
        RestTemplate http = new RestTemplate();
        PromptTranslationCacheService cache = mock(PromptTranslationCacheService.class);
        when(cache.find("A cached scene.")).thenReturn("已缓存的场景。");
        when(cache.find("No text.")).thenReturn("不要文字。");
        LibreTranslateService service = new LibreTranslateService("http://translate.local/translate", "", http, cache);
        PromptTranslationDTO dto = new PromptTranslationDTO();
        dto.setPositivePrompt("A cached scene."); dto.setNegativePrompt("No text.");

        PromptTranslationVO result = service.translateToChinese(dto);

        assertThat(result.getPositivePrompt()).isEqualTo("已缓存的场景。");
        assertThat(result.getNegativePrompt()).isEqualTo("不要文字。");
        verify(cache, never()).save(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test void rejectsIncompleteResponsesAndInvalidLongFormRequests() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        PromptTranslationCacheService cache = mock(PromptTranslationCacheService.class);
        server.expect(requestTo("http://translate.local/translate"))
                .andRespond(withSuccess("{\"translatedText\":[]}", MediaType.APPLICATION_JSON));
        LibreTranslateService service = new LibreTranslateService("http://translate.local/translate", "", http, cache);
        PromptTranslationDTO dto = new PromptTranslationDTO(); dto.setPositivePrompt("Scene"); dto.setNegativePrompt("");
        assertThatThrownBy(() -> service.translateToChinese(dto)).isInstanceOf(PromptTranslationException.class).hasMessageContaining("译文数量");
        dto.setPositivePrompt(" ");
        assertThatThrownBy(() -> service.translateToChinese(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("正向描述不能为空");
    }
}
