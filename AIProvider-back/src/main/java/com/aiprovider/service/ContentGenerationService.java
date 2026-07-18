package com.aiprovider.service;

import com.aiprovider.mapper.ContentAiMapper;
import com.aiprovider.model.dto.CommentReplyRequestDTO;
import com.aiprovider.model.dto.ContentRewriteRequestDTO;
import com.aiprovider.model.vo.ContentGenerationVO;
import com.aiprovider.repository.ContentAiRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContentGenerationService {
    private final ContentAiConfigService configService; private final GeminiContentClient client; private final ContentAiRepository repository; private final ObjectMapper json;
    public ContentGenerationService(ContentAiConfigService configService,GeminiContentClient client,ContentAiRepository repository,ObjectMapper json){this.configService=configService;this.client=client;this.repository=repository;this.json=json;}

    public ContentGenerationVO rewrite(ContentRewriteRequestDTO dto){
        if(dto==null)throw new IllegalArgumentException("内容改写请求不能为空");String source=required(dto.getSourceText(),"来源内容",30000);
        Map<String,Object> input=new LinkedHashMap<>();input.put("sourceText",source);input.put("sourceAuthor",optional(dto.getSourceAuthor(),255));input.put("sourceUrl",optional(dto.getSourceUrl(),1000));input.put("extraInstruction",optional(dto.getExtraInstruction(),4000));
        String user="来源作者："+orUnknown(dto.getSourceAuthor())+"\n来源地址："+orUnknown(dto.getSourceUrl())+"\n来源内容：\n"+source+extra(dto.getExtraInstruction());
        GeminiRuntimeConfig config=configService.runtime();return execute("CONTENT_REWRITE",config,config.contentRewritePrompt,user,input);
    }

    public ContentGenerationVO reply(CommentReplyRequestDTO dto){
        if(dto==null)throw new IllegalArgumentException("评论回复请求不能为空");String comment=required(dto.getCommentText(),"用户评论",10000);
        Map<String,Object> input=new LinkedHashMap<>();input.put("postTitle",optional(dto.getPostTitle(),255));input.put("postBody",optional(dto.getPostBody(),30000));input.put("commentText",comment);input.put("commenterName",optional(dto.getCommenterName(),255));input.put("extraInstruction",optional(dto.getExtraInstruction(),4000));
        String user="笔记标题："+orUnknown(dto.getPostTitle())+"\n笔记正文：\n"+orUnknown(dto.getPostBody())+"\n评论用户："+orUnknown(dto.getCommenterName())+"\n用户评论：\n"+comment+extra(dto.getExtraInstruction());
        GeminiRuntimeConfig config=configService.runtime();return execute("COMMENT_REPLY",config,config.commentReplyPrompt,user,input);
    }

    public ContentGenerationVO testConnection(){GeminiRuntimeConfig config=configService.runtime();Map<String,Object> input=new LinkedHashMap<>();input.put("purpose","connection_test");return execute("CONNECTION_TEST",config,"你是连接测试助手。严格按用户要求返回。","只回复：连接成功",input);}

    private ContentGenerationVO execute(String type,GeminiRuntimeConfig config,String systemPrompt,String userPrompt,Map<String,Object> input){
        ContentAiMapper.GenerationRecord record=new ContentAiMapper.GenerationRecord();record.setGenerationType(type);record.setModelName(config.model);record.setInputJson(json(input));record.setSystemPromptSnapshot(systemPrompt);long id=repository.insertGeneration(record);long started=System.nanoTime();
        try{String output=client.generate(config,systemPrompt,userPrompt);long latency=elapsed(started);repository.markSucceeded(id,output,latency);return new ContentGenerationVO(id,type,"GEMINI",config.model,output,latency,LocalDateTime.now());}
        catch(ContentAiException e){repository.markFailed(id,e.getCode(),limit(e.getMessage(),1000),elapsed(started));throw e;}
        catch(RuntimeException e){repository.markFailed(id,"INTERNAL_ERROR",limit(e.getMessage(),1000),elapsed(started));throw e;}
    }
    private String json(Map<String,Object> input){try{return json.writeValueAsString(input);}catch(JsonProcessingException e){throw new IllegalStateException("生成输入无法序列化",e);}}
    private long elapsed(long started){return Math.max(0,(System.nanoTime()-started)/1_000_000L);} private String extra(String value){String v=optional(value,4000);return v==null?"":"\n额外要求：\n"+v;}
    private String required(String value,String label,int max){String v=optional(value,max);if(v==null)throw new IllegalArgumentException(label+"不能为空");return v;}
    private String optional(String value,int max){if(value==null||value.trim().isEmpty())return null;String v=value.trim();if(v.length()>max)throw new IllegalArgumentException("输入内容长度超过 "+max);return v;}
    private String orUnknown(String value){return value==null||value.trim().isEmpty()?"未提供":value.trim();} private String limit(String value,int max){if(value==null)return "未知错误";return value.length()<=max?value:value.substring(0,max);}
}
