package com.aiprovider.repository;

import com.aiprovider.mapper.ContentAiMapper;
import org.springframework.stereotype.Repository;
import java.util.Map;

@Repository
public class ContentAiRepository {
    private final ContentAiMapper mapper;
    public ContentAiRepository(ContentAiMapper mapper){this.mapper=mapper;}
    public Map<String,Object> findConfig(){return mapper.findConfig();}
    public void updateConfig(ContentAiMapper.ConfigRecord record){if(mapper.updateConfig(record)!=1)throw new IllegalStateException("Gemini 配置更新失败");}
    public long insertGeneration(ContentAiMapper.GenerationRecord record){mapper.insertGeneration(record);return record.getId();}
    public void markSucceeded(long id,String output,long latencyMs){if(mapper.markSucceeded(id,output,latencyMs)!=1)throw new IllegalStateException("生成记录成功状态更新失败");}
    public void markFailed(long id,String code,String message,long latencyMs){mapper.markFailed(id,code,message,latencyMs);}
}
