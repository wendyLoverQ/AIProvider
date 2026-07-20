package com.aiprovider.service;

import com.aiprovider.model.vo.XhsPublicationResultVO;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.*;

@Service
public class XiaohongshuPublicationService {
    private final ContentOperationsRepository repository;private final PlatformAccountCredentialService accountCredentials;private final XiaohongshuWebAdapter adapter;private final XhsTextCardRenderer renderer;private final ObjectMapper json;
    public XiaohongshuPublicationService(ContentOperationsRepository repository,PlatformAccountCredentialService accountCredentials,XiaohongshuWebAdapter adapter,XhsTextCardRenderer renderer,ObjectMapper json){this.repository=repository;this.accountCredentials=accountCredentials;this.adapter=adapter;this.renderer=renderer;this.json=json;}
    public XhsPublicationResultVO publish(long publicationId){Map<String,Object> task=repository.findPublicationDetails(publicationId);if(task==null)throw new IllegalArgumentException("小红书发布任务不存在");if("PUBLISHED".equals(text(task.get("status"))))return new XhsPublicationResultVO(publicationId,"PUBLISHED",null);if(!truth(task.get("accountEnabled")))throw new IllegalArgumentException("小红书账号已停用");if(task.get("platformAccountId")==null)throw new IllegalArgumentException("请先在账号中心绑定小红书账号");if(!repository.claimPublication(publicationId))throw new IllegalStateException("发布任务当前状态不可执行");Path card=null;try{String title=required(task.get("title"),"草稿标题");String body=required(task.get("body"),"草稿正文");card=renderer.render(publicationId,title,body);String state=accountCredentials.requireSecret(number(task.get("platformAccountId")),"XIAOHONGSHU","STORAGE_STATE");String url=adapter.publish(state,title,body,tags(task.get("tagsJson")),card);repository.markPublicationPublished(publicationId,url);repository.markAccountPublished(number(task.get("accountId")));repository.markContentItemPublished(number(task.get("contentItemId")));return new XhsPublicationResultVO(publicationId,"PUBLISHED",url);}catch(XiaohongshuAutomationException e){if(e.isUncertain())repository.markPublicationUnknown(publicationId,limit(e.getMessage(),1000));else repository.markPublicationFailed(publicationId,"XHS_PUBLISH_FAILED",limit(e.getMessage(),1000));throw e;}catch(RuntimeException e){repository.markPublicationFailed(publicationId,"XHS_PUBLISH_FAILED",limit(e.getMessage(),1000));throw e;}finally{if(card!=null)try{Files.deleteIfExists(card);}catch(Exception ignored){}}}
    private List<String> tags(Object value){if(value==null)return Collections.emptyList();try{JsonNode node=json.readTree(String.valueOf(value));List<String> result=new ArrayList<>();if(node.isArray())for(JsonNode item:node)if(item.isTextual())result.add(item.asText());return result;}catch(JsonProcessingException e){throw new IllegalStateException("草稿标签数据损坏",e);}}
    private String required(Object value,String label){String v=text(value);if(v==null||v.trim().isEmpty())throw new IllegalArgumentException(label+"不能为空");return v.trim();}private String text(Object v){return v==null?null:String.valueOf(v);}private long number(Object v){return ((Number)v).longValue();}private boolean truth(Object v){return v instanceof Boolean?(Boolean)v:v!=null&&((Number)v).intValue()!=0;}private String limit(String v,int max){if(v==null)return "未知错误";return v.length()<=max?v:v.substring(0,max);}
}
