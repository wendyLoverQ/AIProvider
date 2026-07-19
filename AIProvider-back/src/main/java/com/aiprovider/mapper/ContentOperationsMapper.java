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

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AccountHandle accountHandle,PublishMode publishMode,AdapterType adapterType,Enabled enabled,ConnectionStatus connectionStatus,AdapterStatus adapterStatus,SessionEncrypted sessionEncrypted,SessionHint sessionHint,LastError lastError,LastConnectedAt lastConnectedAt,LastPublishedAt lastPublishedAt FROM c_ContentAccounts WHERE Platform='XIAOHONGSHU' ORDER BY Enabled DESC,UpdatedAt DESC")
    List<Map<String,Object>> findAccounts();

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AccountHandle accountHandle,PublishMode publishMode,AdapterType adapterType,Enabled enabled,ConnectionStatus connectionStatus,AdapterStatus adapterStatus,SessionEncrypted sessionEncrypted,SessionHint sessionHint,LastError lastError,LastConnectedAt lastConnectedAt,LastPublishedAt lastPublishedAt FROM c_ContentAccounts WHERE Id=#{id} AND Platform='XIAOHONGSHU'")
    Map<String,Object> findAccount(long id);

    @Insert("INSERT INTO c_ContentAccounts(Platform,DisplayName,AccountHandle,PublishMode,Enabled,ConnectionStatus,AdapterStatus) VALUES('XIAOHONGSHU',#{displayName},#{accountHandle},#{publishMode},TRUE,'NOT_CONFIGURED','NOT_CONFIGURED')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertAccount(AccountRecord record);

    @Update("UPDATE c_ContentAccounts SET PublishMode=#{publishMode},Enabled=#{enabled} WHERE Id=#{id} AND Platform='XIAOHONGSHU'")
    int updateAccountMode(@Param("id") long id,@Param("publishMode") String publishMode,@Param("enabled") boolean enabled);

    @Update("UPDATE c_ContentAccounts SET SessionEncrypted=#{encrypted},SessionHint=#{hint},ConnectionStatus='CONNECTED',AdapterStatus='READY',LastError=NULL,LastConnectedAt=NOW(3) WHERE Id=#{id} AND Platform='XIAOHONGSHU'")
    int updateAccountSession(@Param("id") long id,@Param("encrypted") String encrypted,@Param("hint") String hint);

    @Select("SELECT s.Id id,s.Platform platform,s.Name name,s.SourceType sourceType,s.ExternalUid externalUid,s.ExternalHandle externalHandle,s.AdapterType adapterType,s.SourceUrl sourceUrl,COALESCE(c.CredentialEncrypted,s.CredentialEncrypted) credentialEncrypted,COALESCE(c.CredentialHint,s.CredentialHint) credentialHint,s.PollIntervalMinutes pollIntervalMinutes,s.FetchLimit fetchLimit,s.Enabled enabled,s.LastStatus lastStatus,s.LastCollectedAt lastCollectedAt,s.LastTestedAt lastTestedAt,c.Id collectionAccountId,c.DisplayName collectionAccountName FROM c_ContentSources s LEFT JOIN c_ContentSourceCollectionAccounts b ON b.SourceId=s.Id LEFT JOIN c_ContentCollectionAccounts c ON c.Id=b.CollectionAccountId ORDER BY s.Enabled DESC,s.UpdatedAt DESC")
    List<Map<String,Object>> findSources();

    @Select("SELECT s.Id id,s.Platform platform,s.Name name,s.SourceType sourceType,s.ExternalUid externalUid,s.ExternalHandle externalHandle,s.AdapterType adapterType,s.SourceUrl sourceUrl,COALESCE(c.CredentialEncrypted,s.CredentialEncrypted) credentialEncrypted,COALESCE(c.CredentialHint,s.CredentialHint) credentialHint,s.PollIntervalMinutes pollIntervalMinutes,s.FetchLimit fetchLimit,s.Enabled enabled,s.LastStatus lastStatus,s.LastCollectedAt lastCollectedAt,s.LastTestedAt lastTestedAt,c.Id collectionAccountId,c.DisplayName collectionAccountName FROM c_ContentSources s LEFT JOIN c_ContentSourceCollectionAccounts b ON b.SourceId=s.Id LEFT JOIN c_ContentCollectionAccounts c ON c.Id=b.CollectionAccountId WHERE s.Id=#{id}")
    Map<String,Object> findSource(long id);

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AdapterType adapterType,CredentialEncrypted credentialEncrypted,CredentialHint credentialHint,Enabled enabled FROM c_ContentCollectionAccounts ORDER BY Enabled DESC,UpdatedAt DESC")
    List<Map<String,Object>> findCollectionAccounts();

    @Select("SELECT Id id,Platform platform,DisplayName displayName,AdapterType adapterType,CredentialEncrypted credentialEncrypted,CredentialHint credentialHint,Enabled enabled FROM c_ContentCollectionAccounts WHERE Id=#{id}")
    Map<String,Object> findCollectionAccount(long id);

    @Insert("INSERT INTO c_ContentCollectionAccounts(Platform,DisplayName,AdapterType,CredentialEncrypted,CredentialHint,Enabled) VALUES('TWITTER',#{displayName},#{adapterType},#{credentialEncrypted},#{credentialHint},TRUE)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertCollectionAccount(CollectionAccountRecord record);

    @Insert("INSERT INTO c_ContentSourceCollectionAccounts(SourceId,CollectionAccountId) VALUES(#{sourceId},#{collectionAccountId})")
    int insertSourceCollectionAccount(@Param("sourceId") long sourceId,@Param("collectionAccountId") long collectionAccountId);

    @Insert("INSERT INTO c_ContentSources(Platform,SourceType,ExternalUid,ExternalHandle,AdapterType,Name,SourceUrl,CredentialEncrypted,CredentialHint,PollIntervalMinutes,FetchLimit,Enabled) VALUES(#{platform},#{sourceType},#{externalUid},#{externalHandle},#{adapterType},#{name},#{sourceUrl},#{credentialEncrypted},#{credentialHint},#{pollIntervalMinutes},#{fetchLimit},TRUE)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertSource(SourceRecord record);

    @Insert("INSERT IGNORE INTO c_ContentItems(SourceId,ExternalId,SourceUrl,AuthorName,RawText,RawPayloadJson,PublishedAt,ProcessingStatus,FetchedByRunType) VALUES(#{sourceId},#{externalId},#{sourceUrl},#{authorName},#{rawText},CAST(#{rawPayloadJson} AS JSON),#{publishedAt},'COLLECTED',#{fetchedByRunType})")
    int insertContentItem(ContentItemRecord record);

    @Select("SELECT Id id,SourceId sourceId,ExternalId externalId,SourceUrl sourceUrl,AuthorName authorName,RawText rawText,PublishedAt publishedAt,ProcessingStatus processingStatus,RelevanceStatus relevanceStatus,RelevanceScore relevanceScore,RelevanceReason relevanceReason,RelevanceCheckedAt relevanceCheckedAt,CollectedAt collectedAt FROM c_ContentItems WHERE SourceId=#{sourceId} ORDER BY COALESCE(PublishedAt,CollectedAt) DESC LIMIT #{limit}")
    List<Map<String,Object>> findContentItems(@Param("sourceId") long sourceId,@Param("limit") int limit);

    @Select("SELECT Id id,SourceId sourceId,ExternalId externalId,SourceUrl sourceUrl,AuthorName authorName,RawText rawText,PublishedAt publishedAt,ProcessingStatus processingStatus,RelevanceStatus relevanceStatus,RelevanceScore relevanceScore,RelevanceReason relevanceReason,RelevanceCheckedAt relevanceCheckedAt,CollectedAt collectedAt FROM c_ContentItems WHERE Id=#{id}")
    Map<String,Object> findContentItem(long id);

    @Update("UPDATE c_ContentItems SET RelevanceStatus=#{status},RelevanceScore=#{score},RelevanceReason=#{reason},RelevanceCheckedAt=NOW(3),ProcessingStatus=CASE WHEN #{status}='RELEVANT' THEN 'RELEVANT' WHEN #{status}='IRRELEVANT' THEN 'FILTERED' WHEN #{status}='FAILED' THEN 'CLASSIFICATION_FAILED' ELSE ProcessingStatus END WHERE Id=#{id}")
    int updateContentRelevance(@Param("id") long id,@Param("status") String status,@Param("score") java.math.BigDecimal score,@Param("reason") String reason);

    @Update("UPDATE c_ContentSources SET LastStatus='SUCCESS',LastError=NULL,LastCollectedAt=NOW(3),LastTestedAt=NOW(3) WHERE Id=#{id}")
    int markSourceTestSucceeded(long id);

    @Update("UPDATE c_ContentSources SET LastStatus='FAILED',LastError=#{error},LastTestedAt=NOW(3) WHERE Id=#{id}")
    int markSourceTestFailed(@Param("id") long id,@Param("error") String error);

    @Select("SELECT SourceId FROM c_ContentAccountSources WHERE AccountId=#{accountId} AND Enabled=TRUE ORDER BY SourceId")
    List<Long> findAccountSourceIds(long accountId);

    @Select("SELECT a.Id accountId,s.Id sourceId FROM c_ContentAccounts a JOIN c_ContentAccountSources b ON b.AccountId=a.Id AND b.Enabled=TRUE JOIN c_ContentSources s ON s.Id=b.SourceId AND s.Enabled=TRUE JOIN c_ContentOperationSettings cfg ON cfg.Id=1 WHERE cfg.AutomationEnabled=TRUE AND a.Platform='XIAOHONGSHU' AND a.Enabled=TRUE AND a.PublishMode='AUTO' AND a.ConnectionStatus='CONNECTED' AND a.SessionEncrypted IS NOT NULL AND (s.LastCollectedAt IS NULL OR TIMESTAMPDIFF(MINUTE,s.LastCollectedAt,NOW(3))>=cfg.CrawlIntervalMinutes) ORDER BY s.Id,a.Id")
    List<Map<String,Object>> findDueBindings();

    @Update("UPDATE c_ContentSources SET PollIntervalMinutes=#{minutes}")
    int updateAllSourcePollIntervals(int minutes);

    @Insert("INSERT INTO c_ContentOperationRuns(RunType,Platform,Status,TriggerType) VALUES(#{runType},'XIAOHONGSHU','PROCESSING',#{triggerType})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertOperationRun(OperationRunRecord record);

    @Update("UPDATE c_ContentOperationRuns SET Status='SUCCEEDED',MetricsJson=CAST(#{metricsJson} AS JSON),FinishedAt=NOW(3) WHERE Id=#{id} AND Status='PROCESSING'")
    int finishOperationRun(@Param("id") long id,@Param("metricsJson") String metricsJson);

    @Update("UPDATE c_ContentOperationRuns SET Status='FAILED',ErrorMessage=#{error},FinishedAt=NOW(3) WHERE Id=#{id} AND Status='PROCESSING'")
    int failOperationRun(@Param("id") long id,@Param("error") String error);

    @Select("SELECT Id id,ContentItemId contentItemId,Platform platform,Title title,Body body,TagsJson tagsJson,ModelName modelName,ReviewStatus reviewStatus FROM c_ContentDrafts WHERE ContentItemId=#{contentItemId} AND Platform=#{platform}")
    Map<String,Object> findDraft(@Param("contentItemId") long contentItemId,@Param("platform") String platform);

    @Insert("INSERT IGNORE INTO c_ContentDrafts(ContentItemId,Platform,Title,Body,TagsJson,ModelName,PromptVersion,ReviewStatus) VALUES(#{contentItemId},#{platform},#{title},#{body},CAST(#{tagsJson} AS JSON),#{modelName},#{promptVersion},'READY')")
    int insertDraft(DraftRecord record);

    @Insert("INSERT IGNORE INTO c_ContentPublications(DraftId,AccountId,PublishMode,Status,ScheduledAt) VALUES(#{draftId},#{accountId},#{publishMode},#{status},NOW(3))")
    int insertPublication(PublicationRecord record);

    @Select("SELECT Id FROM c_ContentPublications WHERE DraftId=#{draftId} AND AccountId=#{accountId}")
    Long findPublicationId(@Param("draftId") long draftId,@Param("accountId") long accountId);

    @Select("SELECT p.Id id,p.Status status,p.AccountId accountId,p.DraftId draftId,a.DisplayName displayName,a.AccountHandle accountHandle,a.SessionEncrypted sessionEncrypted,a.Enabled accountEnabled,d.ContentItemId contentItemId,d.Title title,d.Body body,d.TagsJson tagsJson FROM c_ContentPublications p JOIN c_ContentAccounts a ON a.Id=p.AccountId JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE p.Id=#{id} AND d.Platform='XIAOHONGSHU'")
    Map<String,Object> findPublicationDetails(long id);

    @Update("UPDATE c_ContentPublications SET Status='PROCESSING',AttemptCount=AttemptCount+1,StartedAt=NOW(3),ErrorCode=NULL,ErrorMessage=NULL WHERE Id=#{id} AND Status IN ('PENDING','FAILED')")
    int claimPublication(long id);

    @Update("UPDATE c_ContentPublications SET Status='PUBLISHED',ExternalPostUrl=#{url},PublishedAt=NOW(3),ErrorCode=NULL,ErrorMessage=NULL WHERE Id=#{id} AND Status='PROCESSING'")
    int markPublicationPublished(@Param("id") long id,@Param("url") String url);

    @Update("UPDATE c_ContentPublications SET Status='FAILED',ErrorCode=#{code},ErrorMessage=#{message} WHERE Id=#{id} AND Status='PROCESSING'")
    int markPublicationFailed(@Param("id") long id,@Param("code") String code,@Param("message") String message);

    @Update("UPDATE c_ContentPublications SET Status='UNKNOWN',ErrorCode='RESULT_UNCERTAIN',ErrorMessage=#{message} WHERE Id=#{id} AND Status='PROCESSING'")
    int markPublicationUnknown(@Param("id") long id,@Param("message") String message);

    @Update("UPDATE c_ContentAccounts SET LastPublishedAt=NOW(3),LastError=NULL WHERE Id=#{id}")
    int markAccountPublished(long id);

    @Update("UPDATE c_ContentItems SET ProcessingStatus='PUBLISHED' WHERE Id=#{id}")
    int markContentItemPublished(long id);

    @Delete("DELETE FROM c_ContentAccountSources WHERE AccountId=#{accountId}")
    int deleteAccountSources(long accountId);

    @Insert("INSERT INTO c_ContentAccountSources(AccountId,SourceId,Enabled) VALUES(#{accountId},#{sourceId},TRUE)")
    int insertAccountSource(@Param("accountId") long accountId,@Param("sourceId") long sourceId);

    @Select("SELECT COUNT(*) FROM c_ContentSources WHERE Id=#{id} AND Enabled=TRUE")
    int countEnabledSource(long id);

    @Select("SELECT p.Id id,d.Title title,a.DisplayName accountName,p.PublishMode publishMode,p.Status status,p.AttemptCount attemptCount,p.ErrorCode errorCode,p.ErrorMessage errorMessage,p.ScheduledAt scheduledAt,p.StartedAt startedAt,p.PublishedAt publishedAt FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId JOIN c_ContentAccounts a ON a.Id=p.AccountId WHERE d.Platform='XIAOHONGSHU' ORDER BY p.CreatedAt DESC LIMIT 20")
    List<Map<String,Object>> findRecentPublications();

    @Select("SELECT p.Id id,d.Title title,d.Body body,CAST(d.TagsJson AS CHAR) tagsJson,d.ModelName modelName,d.ReviewStatus reviewStatus,a.DisplayName accountName,p.PublishMode publishMode,p.Status status,p.AttemptCount attemptCount,p.ExternalPostUrl externalPostUrl,p.ErrorCode errorCode,p.ErrorMessage errorMessage,p.ScheduledAt scheduledAt,p.StartedAt startedAt,p.PublishedAt publishedAt,i.Id contentItemId,i.AuthorName sourceAuthor,i.RawText sourceText,i.SourceUrl sourceUrl,s.Name sourceName FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId JOIN c_ContentAccounts a ON a.Id=p.AccountId LEFT JOIN c_ContentItems i ON i.Id=d.ContentItemId LEFT JOIN c_ContentSources s ON s.Id=i.SourceId WHERE p.Id=#{id} AND d.Platform='XIAOHONGSHU'")
    Map<String,Object> findPublicationFullDetails(long id);

    @Select("<script>SELECT i.Id id,i.SourceId sourceId,s.Name sourceName,i.ExternalId externalId,i.SourceUrl sourceUrl,i.AuthorName authorName,i.RawText rawText,i.PublishedAt publishedAt,i.ProcessingStatus processingStatus,i.RelevanceStatus relevanceStatus,i.RelevanceScore relevanceScore,i.RelevanceReason relevanceReason,i.RelevanceCheckedAt relevanceCheckedAt,i.CollectedAt collectedAt FROM c_ContentItems i JOIN c_ContentSources s ON s.Id=i.SourceId WHERE (#{sourceId} IS NULL OR i.SourceId=#{sourceId}) <if test='query != null'>AND (i.RawText LIKE CONCAT('%',#{query},'%') OR i.AuthorName LIKE CONCAT('%',#{query},'%') OR s.Name LIKE CONCAT('%',#{query},'%'))</if> ORDER BY i.CollectedAt DESC LIMIT #{limit}</script>")
    List<Map<String,Object>> searchContentItems(@Param("query") String query,@Param("sourceId") Long sourceId,@Param("limit") int limit);

    @Select("SELECT Id id,RunType runType,Platform platform,Status status,TriggerType triggerType,CAST(MetricsJson AS CHAR) metricsJson,ErrorMessage errorMessage,StartedAt startedAt,FinishedAt finishedAt FROM c_ContentOperationRuns ORDER BY StartedAt DESC LIMIT #{limit}")
    List<Map<String,Object>> findRecentOperationRuns(int limit);

    @Select("SELECT COUNT(*) FROM c_ContentItems WHERE CollectedAt >= CURRENT_DATE()") long countCollectedToday();
    @Select("SELECT COUNT(*) FROM c_ContentDrafts WHERE Platform='XIAOHONGSHU' AND ReviewStatus='READY'") long countReadyDrafts();
    @Select("SELECT COUNT(*) FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND p.Status='PUBLISHED' AND p.PublishedAt >= CURRENT_DATE()") long countPublishedToday();
    @Select("SELECT COUNT(*) FROM c_ContentComments c JOIN c_ContentPublications p ON p.Id=c.PublicationId JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND c.ReplyStatus='PENDING'") long countPendingComments();
    @Select("SELECT COUNT(*) FROM c_ContentPublications p JOIN c_ContentDrafts d ON d.Id=p.DraftId WHERE d.Platform='XIAOHONGSHU' AND p.Status='FAILED'") long countFailedPublications();

    class AccountRecord { private Long id; private String displayName; private String accountHandle; private String publishMode;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
        public String getAccountHandle(){return accountHandle;} public void setAccountHandle(String v){accountHandle=v;} public String getPublishMode(){return publishMode;} public void setPublishMode(String v){publishMode=v;} }
    class CollectionAccountRecord {private Long id;private String displayName;private String adapterType;private String credentialEncrypted;private String credentialHint;
        public Long getId(){return id;}public void setId(Long v){id=v;}public String getDisplayName(){return displayName;}public void setDisplayName(String v){displayName=v;}public String getAdapterType(){return adapterType;}public void setAdapterType(String v){adapterType=v;}public String getCredentialEncrypted(){return credentialEncrypted;}public void setCredentialEncrypted(String v){credentialEncrypted=v;}public String getCredentialHint(){return credentialHint;}public void setCredentialHint(String v){credentialHint=v;}}
    class SourceRecord { private Long id; private String platform; private String sourceType; private String externalUid; private String externalHandle; private String adapterType; private String name; private String sourceUrl; private String credentialEncrypted; private String credentialHint; private int pollIntervalMinutes; private int fetchLimit;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getPlatform(){return platform;} public void setPlatform(String v){platform=v;} public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
        public String getExternalUid(){return externalUid;} public void setExternalUid(String v){externalUid=v;} public String getExternalHandle(){return externalHandle;} public void setExternalHandle(String v){externalHandle=v;} public String getAdapterType(){return adapterType;} public void setAdapterType(String v){adapterType=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
        public String getCredentialEncrypted(){return credentialEncrypted;} public void setCredentialEncrypted(String v){credentialEncrypted=v;} public String getCredentialHint(){return credentialHint;} public void setCredentialHint(String v){credentialHint=v;} public int getPollIntervalMinutes(){return pollIntervalMinutes;} public void setPollIntervalMinutes(int v){pollIntervalMinutes=v;} public int getFetchLimit(){return fetchLimit;} public void setFetchLimit(int v){fetchLimit=v;} }
    class ContentItemRecord { private Long sourceId; private String externalId; private String sourceUrl; private String authorName; private String rawText; private String rawPayloadJson; private java.time.LocalDateTime publishedAt; private String fetchedByRunType;
        public Long getSourceId(){return sourceId;} public void setSourceId(Long v){sourceId=v;} public String getExternalId(){return externalId;} public void setExternalId(String v){externalId=v;} public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;} public String getAuthorName(){return authorName;} public void setAuthorName(String v){authorName=v;}
        public String getRawText(){return rawText;} public void setRawText(String v){rawText=v;} public String getRawPayloadJson(){return rawPayloadJson;} public void setRawPayloadJson(String v){rawPayloadJson=v;} public java.time.LocalDateTime getPublishedAt(){return publishedAt;} public void setPublishedAt(java.time.LocalDateTime v){publishedAt=v;} public String getFetchedByRunType(){return fetchedByRunType;} public void setFetchedByRunType(String v){fetchedByRunType=v;} }
    class DraftRecord {private Long contentItemId;private String platform;private String title;private String body;private String tagsJson;private String modelName;private String promptVersion;
        public Long getContentItemId(){return contentItemId;}public void setContentItemId(Long v){contentItemId=v;}public String getPlatform(){return platform;}public void setPlatform(String v){platform=v;}public String getTitle(){return title;}public void setTitle(String v){title=v;}public String getBody(){return body;}public void setBody(String v){body=v;}public String getTagsJson(){return tagsJson;}public void setTagsJson(String v){tagsJson=v;}public String getModelName(){return modelName;}public void setModelName(String v){modelName=v;}public String getPromptVersion(){return promptVersion;}public void setPromptVersion(String v){promptVersion=v;}}
    class PublicationRecord {private Long draftId;private Long accountId;private String publishMode;private String status;
        public Long getDraftId(){return draftId;}public void setDraftId(Long v){draftId=v;}public Long getAccountId(){return accountId;}public void setAccountId(Long v){accountId=v;}public String getPublishMode(){return publishMode;}public void setPublishMode(String v){publishMode=v;}public String getStatus(){return status;}public void setStatus(String v){status=v;}}
    class OperationRunRecord {private Long id;private String runType;private String triggerType;public Long getId(){return id;}public void setId(Long v){id=v;}public String getRunType(){return runType;}public void setRunType(String v){runType=v;}public String getTriggerType(){return triggerType;}public void setTriggerType(String v){triggerType=v;}}
    class SettingsRecord { private boolean automationEnabled; private String defaultPublishMode; private int crawlIntervalMinutes; private int commentIntervalMinutes; private String contentModel;
        public boolean isAutomationEnabled(){return automationEnabled;} public void setAutomationEnabled(boolean v){automationEnabled=v;} public String getDefaultPublishMode(){return defaultPublishMode;} public void setDefaultPublishMode(String v){defaultPublishMode=v;}
        public int getCrawlIntervalMinutes(){return crawlIntervalMinutes;} public void setCrawlIntervalMinutes(int v){crawlIntervalMinutes=v;} public int getCommentIntervalMinutes(){return commentIntervalMinutes;} public void setCommentIntervalMinutes(int v){commentIntervalMinutes=v;}
        public String getContentModel(){return contentModel;} public void setContentModel(String v){contentModel=v;} }
}
