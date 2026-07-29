package com.aiprovider.service;

import com.aiprovider.mapper.ContentAiMapper;
import com.aiprovider.model.vo.ContentRelevanceVO;
import com.aiprovider.repository.ContentAiRepository;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContentRelevanceService {
    private final ContentAiConfigService configService;
    private final GeminiContentClient client;
    private final ContentAiRepository aiRepository;
    private final ContentOperationsRepository contentRepository;
    private final ObjectMapper json;

    public ContentRelevanceService(ContentAiConfigService configService, GeminiContentClient client, ContentAiRepository aiRepository, ContentOperationsRepository contentRepository, ObjectMapper json) {
        this.configService=configService;this.client=client;this.aiRepository=aiRepository;this.contentRepository=contentRepository;this.json=json;
    }

    public ContentRelevanceVO classify(long contentItemId) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.ContentRelevanceService.classify", new String[] { "contentItemId" }, new Object[] { contentItemId });
    Map<String,Object> item=contentRepository.findContentItem(contentItemId);
        if(item==null)throw new IllegalArgumentException("待判断内容不存在");
        String sourceText=required(item.get("rawText"),"来源内容");
        GeminiRuntimeConfig config=configService.runtime();
        Map<String,Object> input=new LinkedHashMap<>();
        input.put("contentItemId",contentItemId);input.put("authorName",text(item.get("authorName")));input.put("sourceUrl",text(item.get("sourceUrl")));input.put("sourceText",sourceText);
        String userPrompt="请判断下面这条内容是否与 AI 相关。只返回 JSON 对象，不要返回 Markdown。\n"
                +"JSON 格式：{\"relevant\":true或false,\"score\":0到1之间的数字,\"reason\":\"简短中文原因\"}\n"
                +"来源作者："+unknown(item.get("authorName"))+"\n来源地址："+unknown(item.get("sourceUrl"))+"\n来源内容：\n"+sourceText;
        ContentAiMapper.GenerationRecord record=new ContentAiMapper.GenerationRecord();record.setContentItemId(contentItemId);record.setGenerationType("RELEVANCE_CLASSIFICATION");record.setModelName(config.model);record.setInputJson(write(input));record.setSystemPromptSnapshot(config.relevancePrompt);
        long generationId=aiRepository.insertGeneration(record);long started=System.nanoTime();
        try {
            String output=client.generateJson(config,config.relevancePrompt,userPrompt);
            Decision decision=parse(output);String status=decision.relevant?"RELEVANT":"IRRELEVANT";
            aiRepository.markSucceeded(generationId,output,elapsed(started));
            contentRepository.updateContentRelevance(contentItemId,status,decision.score,decision.reason);
            return com.aiprovider.logging.BusinessOperationLogger.success("service.ContentRelevanceService.classify", new ContentRelevanceVO(contentItemId,decision.relevant,decision.score,decision.reason,config.model,LocalDateTime.now()));
        } catch(ContentAiException e) {
            aiRepository.markFailed(generationId,e.getCode(),limit(e.getMessage(),1000),elapsed(started));
            contentRepository.updateContentRelevance(contentItemId,"FAILED",null,limit(e.getMessage(),1000));throw e;
        } catch(RuntimeException e) {
            aiRepository.markFailed(generationId,"INVALID_CLASSIFICATION",limit(e.getMessage(),1000),elapsed(started));
            contentRepository.updateContentRelevance(contentItemId,"FAILED",null,limit(e.getMessage(),1000));
            if(e instanceof IllegalArgumentException)throw new ContentAiException("INVALID_CLASSIFICATION",e.getMessage(),e);throw e;
        }
    }

    private Decision parse(String value) {
        try {
            JsonNode root=json.readTree(value);
            if(!root.isObject()||!root.has("relevant")||!root.path("relevant").isBoolean()||!root.has("score")||!root.path("score").isNumber()||!root.has("reason")||!root.path("reason").isTextual())throw new IllegalArgumentException("Gemini 相关性判断返回格式不正确");
            BigDecimal score=root.path("score").decimalValue();if(score.compareTo(BigDecimal.ZERO)<0||score.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Gemini 返回的相关度不在 0 到 1 之间");
            String reason=root.path("reason").asText().trim();if(reason.isEmpty())throw new IllegalArgumentException("Gemini 未返回判断原因");
            return new Decision(root.path("relevant").asBoolean(),score,limit(reason,1000));
        } catch(JsonProcessingException e) {throw new IllegalArgumentException("Gemini 相关性判断未返回有效 JSON",e);}
    }
    private String write(Map<String,Object> value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("相关性判断输入无法序列化",e);}}
    private String required(Object value,String label){String v=text(value);if(v==null||v.trim().isEmpty())throw new IllegalArgumentException(label+"不能为空");return v.trim();}
    private String unknown(Object value){String v=text(value);return v==null||v.trim().isEmpty()?"未提供":v.trim();}
    private String text(Object value){return value==null?null:String.valueOf(value);} private String limit(String value,int max){if(value==null)return "未知错误";return value.length()<=max?value:value.substring(0,max);}
    private long elapsed(long started){return Math.max(0,(System.nanoTime()-started)/1_000_000L);}
    private static final class Decision {final boolean relevant;final BigDecimal score;final String reason;Decision(boolean relevant,BigDecimal score,String reason){this.relevant=relevant;this.score=score;this.reason=reason;}}
}
