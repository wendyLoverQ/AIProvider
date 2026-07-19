package com.aiprovider.service;

import com.aiprovider.model.dto.ContentAccountCreateDTO;
import com.aiprovider.model.dto.ContentOperationSettingsDTO;
import com.aiprovider.model.vo.ContentOperationSettingsVO;
import com.aiprovider.repository.ContentOperationsRepository;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContentOperationsServiceTest {
    @Test void rejectsUnknownPublishModeBeforeWriting(){
        ContentOperationsRepository repository=mock(ContentOperationsRepository.class);
        ContentOperationsService service=new ContentOperationsService(repository);
        ContentAccountCreateDTO dto=new ContentAccountCreateDTO();dto.setDisplayName("主账号");dto.setPublishMode("PRIVATE_API");
        assertThrows(IllegalArgumentException.class,()->service.createAccount(dto));verify(repository,never()).insertAccount(any());
    }

    @Test void savesExplicitAutomationSettings(){
        ContentOperationsRepository repository=mock(ContentOperationsRepository.class);
        Map<String,Object> saved=new LinkedHashMap<>();saved.put("automationEnabled",true);saved.put("defaultPublishMode","AUTO");saved.put("crawlIntervalMinutes",240);saved.put("commentIntervalMinutes",30);saved.put("contentModel","gemini");
        when(repository.findSettings()).thenReturn(saved);
        ContentOperationsService service=new ContentOperationsService(repository);ContentOperationSettingsDTO dto=new ContentOperationSettingsDTO();
        dto.setAutomationEnabled(true);dto.setDefaultPublishMode("AUTO");dto.setCrawlIntervalMinutes(240);dto.setCommentIntervalMinutes(30);dto.setContentModel("gemini");
        ContentOperationSettingsVO result=service.updateSettings(dto);assertTrue(result.isAutomationEnabled());assertEquals("AUTO",result.getDefaultPublishMode());verify(repository).updateSettings(any());verify(repository).updateAllSourcePollIntervals(240);
    }
}
