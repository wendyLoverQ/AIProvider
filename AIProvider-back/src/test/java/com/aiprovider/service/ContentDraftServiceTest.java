package com.aiprovider.service;

import com.aiprovider.mapper.ContentAiMapper;
import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.vo.ContentDraftVO;
import com.aiprovider.repository.ContentAiRepository;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentDraftServiceTest {
    @Test void createsStructuredXhsDraftOnlyForRelevantContent(){
        ContentAiConfigService config=mock(ContentAiConfigService.class);GeminiContentClient client=mock(GeminiContentClient.class);ContentAiRepository ai=mock(ContentAiRepository.class);ContentOperationsRepository repository=mock(ContentOperationsRepository.class);
        Map<String,Object> item=new HashMap<>();item.put("rawText","Gemini 发布新模型");item.put("relevanceStatus","RELEVANT");when(repository.findContentItem(5)).thenReturn(item);when(config.runtime()).thenReturn(new GeminiRuntimeConfig(true,"base","gemini","key","relevance","rewrite prompt","reply",BigDecimal.ZERO,512));when(ai.insertGeneration(any(ContentAiMapper.GenerationRecord.class))).thenReturn(10L);when(client.generateDraftJson(any(),anyString(),anyString())).thenReturn("{\"title\":\"Gemini新模型\",\"body\":\"今天聊聊这个更新。\",\"tags\":[\"#AI\",\"Gemini\"]}");
        Map<String,Object> saved=new HashMap<>();saved.put("id",11L);saved.put("contentItemId",5L);saved.put("platform","XIAOHONGSHU");saved.put("title","Gemini新模型");saved.put("body","今天聊聊这个更新。");saved.put("tagsJson","[\"AI\",\"Gemini\"]");saved.put("modelName","gemini");saved.put("reviewStatus","READY");when(repository.findDraft(5,"XIAOHONGSHU")).thenReturn(null,saved);
        ContentDraftVO result=new ContentDraftService(config,client,ai,repository,new ObjectMapper()).createXiaohongshuDraft(5);assertEquals("Gemini新模型",result.getTitle());assertEquals(Arrays.asList("AI","Gemini"),result.getTags());verify(client).generateDraftJson(any(),anyString(),contains("title 必须是 8 到 18 个字符"));verify(repository).insertDraft(argThat((ContentOperationsMapper.DraftRecord r)->r.getTagsJson().contains("Gemini")));verify(ai).markSucceeded(eq(10L),anyString(),anyLong());
    }
    @Test void refusesDraftForFilteredContent(){ContentOperationsRepository repository=mock(ContentOperationsRepository.class);Map<String,Object> item=new HashMap<>();item.put("rawText","unrelated");item.put("relevanceStatus","IRRELEVANT");when(repository.findDraft(5,"XIAOHONGSHU")).thenReturn(null);when(repository.findContentItem(5)).thenReturn(item);ContentDraftService service=new ContentDraftService(mock(ContentAiConfigService.class),mock(GeminiContentClient.class),mock(ContentAiRepository.class),repository,new ObjectMapper());assertThrows(IllegalArgumentException.class,()->service.createXiaohongshuDraft(5));verify(repository,never()).insertDraft(any());}
}
