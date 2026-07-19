package com.aiprovider.service;

import com.aiprovider.mapper.ContentAiMapper;
import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.vo.ContentDraftVO;
import com.aiprovider.repository.ContentAiRepository;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ContentDraftService {
    private final ContentAiConfigService configService;private final GeminiContentClient client;private final ContentAiRepository aiRepository;private final ContentOperationsRepository repository;private final ObjectMapper json;
    public ContentDraftService(ContentAiConfigService configService,GeminiContentClient client,ContentAiRepository aiRepository,ContentOperationsRepository repository,ObjectMapper json){this.configService=configService;this.client=client;this.aiRepository=aiRepository;this.repository=repository;this.json=json;}

    public ContentDraftVO createXiaohongshuDraft(long contentItemId){
        Map<String,Object> existing=repository.findDraft(contentItemId,"XIAOHONGSHU");if(existing!=null)return draft(existing);
        Map<String,Object> item=repository.findContentItem(contentItemId);if(item==null)throw new IllegalArgumentException("待改写内容不存在");if(!"RELEVANT".equals(text(item.get("relevanceStatus"))))throw new IllegalArgumentException("只有已判定为 AI 相关的内容才能生成草稿");
        GeminiRuntimeConfig config=configService.runtime();String source=required(item.get("rawText"),"来源内容");Map<String,Object> input=new LinkedHashMap<>();input.put("contentItemId",contentItemId);input.put("sourceAuthor",text(item.get("authorName")));input.put("sourceUrl",text(item.get("sourceUrl")));input.put("sourceText",source);
        String user="请把下面内容改写成小红书纯文本笔记。只返回一个 JSON 对象，不要返回 Markdown、代码围栏或额外说明。\n"
            +"必须严格满足以下规则：\n"
            +"1. title 必须是 8 到 18 个字符，绝对不能超过 20 个字符；汉字、字母、数字和标点均按一个字符计算。若不确定长度，主动改成更短的标题。\n"
            +"2. body 必须是非空纯文本，最多 1000 个字符。\n"
            +"3. tags 必须是字符串数组，最多 10 项，每项最多 30 个字符且不要带 #。\n"
            +"4. JSON 只能包含 title、body、tags 三个字段；返回前先自行检查标题长度和 JSON 结构。\n"
            +"JSON 示例：{\"title\":\"AI新功能值得关注\",\"body\":\"正文\",\"tags\":[\"AI\",\"大模型\"]}\n"
            +"不得虚构来源中没有的事实。\n来源作者："+unknown(item.get("authorName"))+"\n来源地址："+unknown(item.get("sourceUrl"))+"\n来源内容：\n"+source;
        ContentAiMapper.GenerationRecord generation=new ContentAiMapper.GenerationRecord();generation.setContentItemId(contentItemId);generation.setGenerationType("XHS_DRAFT");generation.setModelName(config.model);generation.setInputJson(write(input));generation.setSystemPromptSnapshot(config.contentRewritePrompt);long generationId=aiRepository.insertGeneration(generation);long started=System.nanoTime();
        try{GeneratedDraft generated=generateValidDraft(config,user);ContentOperationsMapper.DraftRecord record=new ContentOperationsMapper.DraftRecord();record.setContentItemId(contentItemId);record.setPlatform("XIAOHONGSHU");record.setTitle(generated.decision.title);record.setBody(generated.decision.body);record.setTagsJson(write(generated.decision.tags));record.setModelName(config.model);record.setPromptVersion("DB_CONFIG");repository.insertDraft(record);Map<String,Object> saved=repository.findDraft(contentItemId,"XIAOHONGSHU");if(saved==null)throw new IllegalStateException("小红书草稿入库失败");aiRepository.markSucceeded(generationId,generated.output,elapsed(started));return draft(saved);}
        catch(ContentAiException e){aiRepository.markFailed(generationId,e.getCode(),limit(e.getMessage(),1000),elapsed(started));throw e;}catch(RuntimeException e){aiRepository.markFailed(generationId,"INVALID_DRAFT",limit(e.getMessage(),1000),elapsed(started));if(e instanceof IllegalArgumentException)throw new ContentAiException("INVALID_DRAFT",e.getMessage(),e);throw e;}
    }
    private GeneratedDraft generateValidDraft(GeminiRuntimeConfig config,String user){String prompt=user;for(int attempt=1;attempt<=2;attempt++){String output=client.generateDraftJson(config,config.contentRewritePrompt,prompt);try{return new GeneratedDraft(output,parse(output));}catch(IllegalArgumentException invalid){if(attempt==2)throw invalid;prompt=user+"\n\n上一次生成未通过校验，原因："+limit(invalid.getMessage(),200)+"。请重新生成全新的 JSON，并在返回前逐项检查所有规则；不要解释错误。";}}throw new IllegalStateException("Gemini 草稿重试状态异常");}
    private DraftDecision parse(String value){try{JsonNode root=json.readTree(value);if(!root.isObject()||!root.path("title").isTextual()||!root.path("body").isTextual()||!root.path("tags").isArray())throw new IllegalArgumentException("Gemini 草稿返回格式不正确");String title=root.path("title").asText().trim();String body=root.path("body").asText().trim();if(title.isEmpty()||title.length()>20)throw new IllegalArgumentException("Gemini 返回的小红书标题为空或超过 20 个字符");if(body.isEmpty()||body.length()>1000)throw new IllegalArgumentException("Gemini 返回的小红书正文为空或超过 1000 个字符");List<String> tags=new ArrayList<>();for(JsonNode tag:root.path("tags")){if(!tag.isTextual())throw new IllegalArgumentException("Gemini 返回的话题标签格式不正确");String valueTag=tag.asText().trim().replaceFirst("^#+","");if(!valueTag.isEmpty()&&!tags.contains(valueTag)){if(valueTag.length()>30)throw new IllegalArgumentException("Gemini 返回的话题标签过长");tags.add(valueTag);}}if(tags.size()>10)throw new IllegalArgumentException("Gemini 返回的话题标签过多");return new DraftDecision(title,body,tags);}catch(JsonProcessingException e){throw new IllegalArgumentException("Gemini 草稿未返回有效 JSON",e);}}
    private ContentDraftVO draft(Map<String,Object> r){return new ContentDraftVO(number(r.get("id")),number(r.get("contentItemId")),text(r.get("platform")),text(r.get("title")),text(r.get("body")),readTags(r.get("tagsJson")),text(r.get("modelName")),text(r.get("reviewStatus")));}
    private List<String> readTags(Object value){if(value==null)return Collections.emptyList();try{JsonNode node=json.readTree(String.valueOf(value));List<String> result=new ArrayList<>();if(node.isArray())for(JsonNode tag:node)result.add(tag.asText());return result;}catch(JsonProcessingException e){throw new IllegalStateException("草稿标签数据损坏",e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("草稿数据无法序列化",e);}}private String required(Object value,String label){String v=text(value);if(v==null||v.trim().isEmpty())throw new IllegalArgumentException(label+"不能为空");return v.trim();}private String unknown(Object value){String v=text(value);return v==null||v.trim().isEmpty()?"未提供":v.trim();}private String text(Object v){return v==null?null:String.valueOf(v);}private Long number(Object v){return v==null?null:((Number)v).longValue();}private long elapsed(long started){return Math.max(0,(System.nanoTime()-started)/1_000_000L);}private String limit(String v,int max){if(v==null)return "未知错误";return v.length()<=max?v:v.substring(0,max);}private static final class DraftDecision{final String title;final String body;final List<String> tags;DraftDecision(String title,String body,List<String> tags){this.title=title;this.body=body;this.tags=tags;}}private static final class GeneratedDraft{final String output;final DraftDecision decision;GeneratedDraft(String output,DraftDecision decision){this.output=output;this.decision=decision;}}
}
