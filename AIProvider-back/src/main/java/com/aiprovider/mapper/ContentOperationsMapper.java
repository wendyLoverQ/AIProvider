package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContentOperationsMapper {
    @Select("SELECT AutomationEnabled automationEnabled,DefaultPublishMode defaultPublishMode,CrawlIntervalMinutes crawlIntervalMinutes,CommentIntervalMinutes commentIntervalMinutes,ContentModel contentModel,UpdatedAt updatedAt FROM c_ContentOperationSettings WHERE Id=1")
    Map<String,Object> findSettings();

    @Update("UPDATE c_ContentOperationSettings SET AutomationEnabled=#{automationEnabled},DefaultPublishMode=#{defaultPublishMode},CrawlIntervalMinutes=#{crawlIntervalMinutes},CommentIntervalMinutes=#{commentIntervalMinutes},ContentModel=#{contentModel} WHERE Id=1")
    int updateSettings(SettingsRecord record);

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AccountHandle accountHandle,PublishMode publishMode,Enabled enabled,ConnectionStatus connectionStatus,AdapterStatus adapterStatus,LastError lastError,LastPublishedAt lastPublishedAt FROM c_ContentAccounts WHERE Platform='XIAOHONGSHU' ORDER BY Enabled DESC,UpdatedAt DESC")
    List<Map<String,Object>> findAccounts();

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AccountHandle accountHandle,PublishMode publishMode,Enabled enabled,ConnectionStatus connectionStatus,AdapterStatus adapterStatus,LastError lastError,LastPublishedAt lastPublishedAt FROM c_ContentAccounts WHERE Id=#{id} AND Platform='XIAOHONGSHU'")
    Map<String,Object> findAccount(long id);

    @Insert("INSERT INTO c_ContentAccounts(Platform,DisplayName,AccountHandle,PublishMode,Enabled,ConnectionStatus,AdapterStatus) VALUES('XIAOHONGSHU',#{displayName},#{accountHandle},#{publishMode},TRUE,'NOT_CONFIGURED','NOT_CONFIGURED')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertAccount(AccountRecord record);

    @Update("UPDATE c_ContentAccounts SET PublishMode=#{publishMode},Enabled=#{enabled} WHERE Id=#{id} AND Platform='XIAOHONGSHU'")
    int updateAccountMode(@Param("id") long id,@Param("publishMode") String publishMode,@Param("enabled") boolean enabled);

    @Select("SELECT Id id,Name name,SourceType sourceType,SourceUrl sourceUrl,PollIntervalMinutes pollIntervalMinutes,Enabled enabled,LastStatus lastStatus,LastCollectedAt lastCollectedAt FROM c_ContentSources WHERE Platform='XIAOHONGSHU' ORDER BY Enabled DESC,UpdatedAt DESC")
    List<Map<String,Object>> findSources();

    @Insert("INSERT INTO c_ContentSources(Platform,SourceType,Name,SourceUrl,PollIntervalMinutes,Enabled) VALUES('XIAOHONGSHU',#{sourceType},#{name},#{sourceUrl},#{pollIntervalMinutes},TRUE)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertSource(SourceRecord record);

    @Select("SELECT p.Id id,d.Title title,a.DisplayName accountName,p.PublishMode publishMode,p.Status status,p.AttemptCount attemptCount,p.ErrorMessage errorMessage,p.ScheduledAt scheduledAt FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId JOIN c_ContentAccounts a ON a.Id=p.AccountId WHERE d.Platform='XIAOHONGSHU' ORDER BY p.CreatedAt DESC LIMIT 20")
    List<Map<String,Object>> findRecentPublications();

    @Select("SELECT COUNT(*) FROM c_ContentItems i JOIN c_ContentSources s ON s.Id=i.SourceId WHERE s.Platform='XIAOHONGSHU' AND i.CollectedAt >= CURRENT_DATE()") long countCollectedToday();
    @Select("SELECT COUNT(*) FROM c_ContentDrafts WHERE Platform='XIAOHONGSHU' AND ReviewStatus='READY'") long countReadyDrafts();
    @Select("SELECT COUNT(*) FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND p.Status='PUBLISHED' AND p.PublishedAt >= CURRENT_DATE()") long countPublishedToday();
    @Select("SELECT COUNT(*) FROM c_ContentComments c JOIN c_ContentPublications p ON p.Id=c.PublicationId JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND c.ReplyStatus='PENDING'") long countPendingComments();
    @Select("SELECT COUNT(*) FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND p.Status='FAILED'") long countFailedPublications();

    class AccountRecord { private Long id; private String displayName; private String accountHandle; private String publishMode;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
        public String getAccountHandle(){return accountHandle;} public void setAccountHandle(String v){accountHandle=v;} public String getPublishMode(){return publishMode;} public void setPublishMode(String v){publishMode=v;} }
    class SourceRecord { private Long id; private String sourceType; private String name; private String sourceUrl; private int pollIntervalMinutes;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
        public String getName(){return name;} public void setName(String v){name=v;} public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
        public int getPollIntervalMinutes(){return pollIntervalMinutes;} public void setPollIntervalMinutes(int v){pollIntervalMinutes=v;} }
    class SettingsRecord { private boolean automationEnabled; private String defaultPublishMode; private int crawlIntervalMinutes; private int commentIntervalMinutes; private String contentModel;
        public boolean isAutomationEnabled(){return automationEnabled;} public void setAutomationEnabled(boolean v){automationEnabled=v;} public String getDefaultPublishMode(){return defaultPublishMode;} public void setDefaultPublishMode(String v){defaultPublishMode=v;}
        public int getCrawlIntervalMinutes(){return crawlIntervalMinutes;} public void setCrawlIntervalMinutes(int v){crawlIntervalMinutes=v;} public int getCommentIntervalMinutes(){return commentIntervalMinutes;} public void setCommentIntervalMinutes(int v){commentIntervalMinutes=v;}
        public String getContentModel(){return contentModel;} public void setContentModel(String v){contentModel=v;} }
}
