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
    @Test void preservesStoredSecretWhenApiKeyFieldIsBlank(){
        ContentAiRepository repository=mock(ContentAiRepository.class);ContentAiSecretCipher cipher=mock(ContentAiSecretCipher.class);
        Map<String,Object> current=config();when(repository.findConfig()).thenReturn(current);ContentAiConfigService service=new ContentAiConfigService(repository,cipher);
        ContentAiConfigDTO dto=dto();dto.setApiKey("   ");ContentAiConfigVO result=service.save(dto);
        verify(cipher,never()).encrypt(any());verify(repository).updateConfig(argThat(x->"encrypted-value".equals(x.getApiKeyEncrypted())));assertTrue(result.isApiKeyConfigured());assertEquals("••••1234",result.getApiKeyHint());
    }
    @Test void rejectsUnexpectedGeminiHost(){ContentAiRepository repository=mock(ContentAiRepository.class);when(repository.findConfig()).thenReturn(config());ContentAiConfigService service=new ContentAiConfigService(repository,mock(ContentAiSecretCipher.class));ContentAiConfigDTO dto=dto();dto.setApiBaseUrl("https://example.com");assertThrows(IllegalArgumentException.class,()->service.save(dto));verify(repository,never()).updateConfig(any());}
    private static ContentAiConfigDTO dto(){ContentAiConfigDTO d=new ContentAiConfigDTO();d.setEnabled(true);d.setApiBaseUrl("https://generativelanguage.googleapis.com");d.setModel("gemini-3.5-flash");d.setContentRewritePrompt("这是一个长度足够的内容改写系统提示词，用于单元测试验证。");d.setCommentReplyPrompt("这是一个长度足够的评论回复系统提示词，用于单元测试验证。");d.setTemperature(new BigDecimal("0.7"));d.setMaxOutputTokens(2048);return d;}
    private static Map<String,Object> config(){Map<String,Object> m=new HashMap<>();m.put("enabled",true);m.put("apiBaseUrl","https://generativelanguage.googleapis.com");m.put("model","gemini-3.5-flash");m.put("apiKeyEncrypted","encrypted-value");m.put("apiKeyHint","••••1234");m.put("contentRewritePrompt","这是一个长度足够的内容改写系统提示词，用于单元测试验证。");m.put("commentReplyPrompt","这是一个长度足够的评论回复系统提示词，用于单元测试验证。");m.put("temperature",new BigDecimal("0.7"));m.put("maxOutputTokens",2048);return m;}
}
