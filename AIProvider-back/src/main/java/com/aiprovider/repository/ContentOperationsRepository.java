package com.aiprovider.repository;

import com.aiprovider.mapper.ContentOperationsMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ContentOperationsRepository {
    private final ContentOperationsMapper mapper;
    public ContentOperationsRepository(ContentOperationsMapper mapper){this.mapper=mapper;}
    public Map<String,Object> findSettings(){return mapper.findSettings();}
    public void updateSettings(ContentOperationsMapper.SettingsRecord record){mapper.updateSettings(record);}
    public List<Map<String,Object>> findAccounts(){return mapper.findAccounts();}
    public Map<String,Object> findAccount(long id){return mapper.findAccount(id);}
    public long insertAccount(ContentOperationsMapper.AccountRecord record){mapper.insertAccount(record);return record.getId();}
    public boolean updateAccountMode(long id,String mode,boolean enabled){return mapper.updateAccountMode(id,mode,enabled)>0;}
    public boolean updateAccountSession(long id,String encrypted,String hint){return mapper.updateAccountSession(id,encrypted,hint)>0;}
    public List<Map<String,Object>> findSources(){return mapper.findSources();}
    public Map<String,Object> findSource(long id){return mapper.findSource(id);}
    public long insertSource(ContentOperationsMapper.SourceRecord record){mapper.insertSource(record);return record.getId();}
    public List<Map<String,Object>> findCollectionAccounts(){return mapper.findCollectionAccounts();}
    public Map<String,Object> findCollectionAccount(long id){return mapper.findCollectionAccount(id);}
    public long insertCollectionAccount(ContentOperationsMapper.CollectionAccountRecord record){mapper.insertCollectionAccount(record);return record.getId();}
    public void insertSourceCollectionAccount(long sourceId,long collectionAccountId){if(mapper.insertSourceCollectionAccount(sourceId,collectionAccountId)!=1)throw new IllegalStateException("采集源账号绑定失败");}
    public int insertContentItem(ContentOperationsMapper.ContentItemRecord record){return mapper.insertContentItem(record);}
    public List<Map<String,Object>> findContentItems(long sourceId,int limit){return mapper.findContentItems(sourceId,limit);}
    public Map<String,Object> findContentItem(long id){return mapper.findContentItem(id);}
    public void updateContentRelevance(long id,String status,java.math.BigDecimal score,String reason){if(mapper.updateContentRelevance(id,status,score,reason)!=1)throw new IllegalStateException("内容相关性状态更新失败");}
    public void markSourceTestSucceeded(long id){mapper.markSourceTestSucceeded(id);}
    public void markSourceTestFailed(long id,String error){mapper.markSourceTestFailed(id,error);}
    public List<Long> findAccountSourceIds(long accountId){return mapper.findAccountSourceIds(accountId);}
    public List<Map<String,Object>> findDueBindings(){return mapper.findDueBindings();}
    public long insertOperationRun(ContentOperationsMapper.OperationRunRecord record){mapper.insertOperationRun(record);return record.getId();}
    public void finishOperationRun(long id,String metricsJson){if(mapper.finishOperationRun(id,metricsJson)!=1)throw new IllegalStateException("运行记录成功状态更新失败");}
    public void failOperationRun(long id,String error){mapper.failOperationRun(id,error);}
    public Map<String,Object> findDraft(long contentItemId,String platform){return mapper.findDraft(contentItemId,platform);}
    public int insertDraft(ContentOperationsMapper.DraftRecord record){return mapper.insertDraft(record);}
    public int insertPublication(ContentOperationsMapper.PublicationRecord record){return mapper.insertPublication(record);}
    public Long findPublicationId(long draftId,long accountId){return mapper.findPublicationId(draftId,accountId);}
    public Map<String,Object> findPublicationDetails(long id){return mapper.findPublicationDetails(id);}
    public boolean claimPublication(long id){return mapper.claimPublication(id)==1;}
    public void markPublicationPublished(long id,String url){if(mapper.markPublicationPublished(id,url)!=1)throw new IllegalStateException("发布任务成功状态更新失败");}
    public void markPublicationFailed(long id,String code,String message){mapper.markPublicationFailed(id,code,message);}
    public void markPublicationUnknown(long id,String message){mapper.markPublicationUnknown(id,message);}
    public void markAccountPublished(long id){mapper.markAccountPublished(id);}public void markContentItemPublished(long id){mapper.markContentItemPublished(id);}
    public void deleteAccountSources(long accountId){mapper.deleteAccountSources(accountId);}
    public void insertAccountSource(long accountId,long sourceId){mapper.insertAccountSource(accountId,sourceId);}
    public boolean isEnabledSource(long id){return mapper.countEnabledSource(id)>0;}
    public List<Map<String,Object>> findRecentPublications(){return mapper.findRecentPublications();}
    public long countCollectedToday(){return mapper.countCollectedToday();} public long countReadyDrafts(){return mapper.countReadyDrafts();}
    public long countPublishedToday(){return mapper.countPublishedToday();} public long countPendingComments(){return mapper.countPendingComments();}
    public long countFailedPublications(){return mapper.countFailedPublications();}
}
