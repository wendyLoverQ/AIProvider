package com.aiprovider.service;

import com.aiprovider.mapper.ContentOperationsMapper;
import com.aiprovider.model.vo.ContentPipelineTestVO;
import com.aiprovider.model.vo.ContentSourceTestVO;
import com.aiprovider.repository.ContentOperationsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ContentAutomationScheduler {
    private static final Logger log=LogManager.getLogger(ContentAutomationScheduler.class);private final ContentOperationsRepository repository;private final ContentSourceService sources;private final ContentPipelineService pipeline;private final ObjectMapper json;private final AtomicBoolean running=new AtomicBoolean(false);
    public ContentAutomationScheduler(ContentOperationsRepository repository,ContentSourceService sources,ContentPipelineService pipeline,ObjectMapper json){this.repository=repository;this.sources=sources;this.pipeline=pipeline;this.json=json;}
    @Scheduled(fixedDelayString="${content-operations.scheduler-delay-ms:60000}",initialDelayString="${content-operations.scheduler-initial-delay-ms:30000}")
    public void runDue(){
        com.aiprovider.logging.BusinessOperationLogger.start("service.ContentAutomationScheduler.runDue", new String[] {}, new Object[] {});
        if(!running.compareAndSet(false,true)){
            com.aiprovider.logging.BusinessOperationLogger.success("service.ContentAutomationScheduler.runDue", null);
            return;
        }try{for(Map<String,Object> source:repository.findDueSources())collect(number(source.get("sourceId")));for(Map<String,Object> binding:repository.findDueIntervalBindings())dispatch(number(binding.get("accountId")),number(binding.get("sourceId")),"INTERVAL");}finally{running.set(false);}}
    private void collect(long sourceId){ContentOperationsMapper.OperationRunRecord run=run("CONTENT_COLLECTION");long id=repository.insertOperationRun(run);try{ContentSourceTestVO result=sources.scheduledFetch(sourceId);Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("sourceId",sourceId);metrics.put("result","COLLECTED");metrics.put("fetchedCount",result.getFetchedCount());metrics.put("newCount",result.getNewCount());repository.finishOperationRun(id,json.writeValueAsString(metrics));if(result.getNewCount()>0)for(Map<String,Object> binding:repository.findImmediateBindings(sourceId))dispatch(number(binding.get("accountId")),sourceId,"IMMEDIATE");}catch(Exception e){repository.failOperationRun(id,limit(e.getMessage(),1000));log.warn("Content collection failed sourceId={} code={}",sourceId,e.getClass().getSimpleName());}}
    private void dispatch(long accountId,long sourceId,String timing){ContentOperationsMapper.OperationRunRecord run=run("CONTENT_PIPELINE");long id=repository.insertOperationRun(run);try{ContentPipelineTestVO result=pipeline.processStoredSource(accountId,sourceId);Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("accountId",accountId);metrics.put("sourceId",sourceId);metrics.put("publishTiming",timing);metrics.put("contentItemId",result.getContentItemId());metrics.put("result",result.getResult());metrics.put("publicationId",result.getPublicationId());repository.finishOperationRun(id,json.writeValueAsString(metrics));}catch(Exception e){repository.failOperationRun(id,limit(e.getMessage(),1000));log.warn("Content pipeline run failed accountId={} sourceId={} code={}",accountId,sourceId,e.getClass().getSimpleName());}finally{try{repository.markBindingDispatched(accountId,sourceId);}catch(Exception e){log.error("Publish rule timestamp update failed accountId={} sourceId={}",accountId,sourceId,e);}}}
    private ContentOperationsMapper.OperationRunRecord run(String type){ContentOperationsMapper.OperationRunRecord run=new ContentOperationsMapper.OperationRunRecord();run.setRunType(type);run.setTriggerType("SCHEDULED");return run;}
    private long number(Object value){return ((Number)value).longValue();}private String limit(String value,int max){if(value==null)return "未知错误";return value.length()<=max?value:value.substring(0,max);}
}
