package com.aiprovider.service;

import com.aiprovider.model.dto.ContentAiConfigDTO;
import com.aiprovider.model.vo.ContentAiConfigVO;
import com.aiprovider.repository.ContentAiRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentAiConfigServiceTest {
    @Test void preservesAccountCenterBindingWhenApiKeyFieldIsBlank(){
        ContentAiRepository repository=mock(ContentAiRepository.class);PlatformAccountCredentialService credentials=mock(PlatformAccountCredentialService.class);
        Map<String,Object> current=config();when(repository.findConfig()).thenReturn(current);ContentAiConfigService service=new ContentAiConfigService(repository,credentials);
        ContentAiConfigDTO dto=dto();dto.setApiKey("   ");ContentAiConfigVO result=service.save(dto);
        verify(repository).updateConfig(any());assertTrue(result.isApiKeyConfigured());assertEquals("账号中心",result.getApiKeyHint());
    }
    @Test void rejectsUnexpectedGeminiHost(){ContentAiRepository repository=mock(ContentAiRepository.class);when(repository.findConfig()).thenReturn(config());ContentAiConfigService service=new ContentAiConfigService(repository,mock(PlatformAccountCredentialService.class));ContentAiConfigDTO dto=dto();dto.setApiBaseUrl("https://example.com");assertThrows(IllegalArgumentException.class,()->service.save(dto));verify(repository,never()).updateConfig(any());}
    @Test void runtimeReadsOnlyTheBoundAccountCenterSecret(){ContentAiRepository repository=mock(ContentAiRepository.class);PlatformAccountCredentialService credentials=mock(PlatformAccountCredentialService.class);when(repository.findConfig()).thenReturn(config());when(credentials.requireSecret(21L,"GEMINI","API_KEY")).thenReturn("runtime-key");GeminiRuntimeConfig runtime=new ContentAiConfigService(repository,credentials).runtime();assertEquals("runtime-key",runtime.apiKey);verify(credentials).requireSecret(21L,"GEMINI","API_KEY");}
    private static ContentAiConfigDTO dto(){ContentAiConfigDTO d=new ContentAiConfigDTO();d.setEnabled(true);d.setApiBaseUrl("https://generativelanguage.googleapis.com");d.setModel("gemini-3.5-flash");d.setRelevancePrompt("这是一个长度足够的人工智能内容相关性判断提示词，用于单元测试验证。");d.setContentRewritePrompt("这是一个长度足够的内容改写系统提示词，用于单元测试验证。");d.setCommentReplyPrompt("这是一个长度足够的评论回复系统提示词，用于单元测试验证。");d.setTemperature(new BigDecimal("0.7"));d.setMaxOutputTokens(2048);return d;}
    private static Map<String,Object> config(){Map<String,Object> m=new HashMap<>();m.put("platformAccountId",21L);m.put("enabled",true);m.put("apiBaseUrl","https://generativelanguage.googleapis.com");m.put("model","gemini-3.5-flash");m.put("relevancePrompt","这是一个长度足够的人工智能内容相关性判断提示词，用于单元测试验证。");m.put("contentRewritePrompt","这是一个长度足够的内容改写系统提示词，用于单元测试验证。");m.put("commentReplyPrompt","这是一个长度足够的评论回复系统提示词，用于单元测试验证。");m.put("temperature",new BigDecimal("0.7"));m.put("maxOutputTokens",2048);return m;}
}
