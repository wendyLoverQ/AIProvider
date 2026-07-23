package com.aiprovider.repository;

import com.aiprovider.mapper.AsrRecordMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class AsrRecordRepository {
    private final AsrRecordMapper mapper;
    public AsrRecordRepository(AsrRecordMapper mapper){this.mapper=mapper;}
    public int insert(AsrRecordMapper.Record row){return mapper.insert(row);}public int assignRecordId(long id,String recordId){return mapper.assignRecordId(id,recordId);}public int markSuccess(long id,String text,Long durationMs,long processingMs,Long requestLimit,Long requestsRemaining,String requestsResetAfter){return mapper.markSuccess(id,text,durationMs,processingMs,requestLimit,requestsRemaining,requestsResetAfter);}public int markFailed(long id,long processingMs,String code,String message){return mapper.markFailed(id,processingMs,code,message);}public int updateCorrection(String recordId,String text){return mapper.updateCorrection(recordId,text);}public Map<String,Object> findByRequestId(String id){return mapper.findByRequestId(id);}public Map<String,Object> findByRecordId(String id){return mapper.findByRecordId(id);}public List<Map<String,Object>> findPage(String status,String provider,String model,String keyword,LocalDateTime start,LocalDateTime end,int offset,int limit){return mapper.findPage(status,provider,model,keyword,start,end,offset,limit);}public long count(String status,String provider,String model,String keyword,LocalDateTime start,LocalDateTime end){return mapper.count(status,provider,model,keyword,start,end);}public List<Map<String,Object>> findProviderModels(){return mapper.findProviderModels();}public Map<String,Object> findLatestQuotaSnapshot(String provider,String model){return mapper.findLatestQuotaSnapshot(provider,model);}public long sumAudioDurationMs(String provider,String model,LocalDateTime start,LocalDateTime end){return mapper.sumAudioDurationMs(provider,model,start,end);}
}
