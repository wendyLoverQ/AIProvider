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
    public List<Map<String,Object>> findSources(){return mapper.findSources();}
    public long insertSource(ContentOperationsMapper.SourceRecord record){mapper.insertSource(record);return record.getId();}
    public List<Map<String,Object>> findRecentPublications(){return mapper.findRecentPublications();}
    public long countCollectedToday(){return mapper.countCollectedToday();} public long countReadyDrafts(){return mapper.countReadyDrafts();}
    public long countPublishedToday(){return mapper.countPublishedToday();} public long countPendingComments(){return mapper.countPendingComments();}
    public long countFailedPublications(){return mapper.countFailedPublications();}
}
