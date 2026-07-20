package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface PlatformAccountMapper {
    @Insert("INSERT INTO c_PlatformAccounts(Platform,AccountKind,DisplayName,AccountHandle,AdapterType,PublicConfigJson,Enabled,ConnectionStatus,CredentialHint,LegacySourceType,LegacySourceId) VALUES(#{platform},#{accountKind},#{displayName},#{accountHandle},#{adapterType},#{publicConfigJson},#{enabled},#{connectionStatus},#{credentialHint},#{legacySourceType},#{legacySourceId})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertAccount(AccountRecord record);

    @Update("UPDATE c_PlatformAccounts SET DisplayName=#{displayName},AccountHandle=#{accountHandle},AdapterType=#{adapterType},PublicConfigJson=#{publicConfigJson},ConnectionStatus=CASE WHEN #{enabled}=FALSE THEN 'DISABLED' WHEN Enabled=FALSE THEN 'NOT_CONFIGURED' ELSE ConnectionStatus END,Enabled=#{enabled} WHERE Id=#{id} AND ArchivedAt IS NULL")
    int updateAccount(AccountRecord record);

    @Select("SELECT Id id,Platform platform,AccountKind accountKind,DisplayName displayName,AccountHandle accountHandle,AdapterType adapterType,CAST(PublicConfigJson AS CHAR) publicConfigJson,Enabled enabled,ConnectionStatus connectionStatus,CredentialHint credentialHint,LastValidatedAt lastValidatedAt,LastConnectedAt lastConnectedAt,LastErrorCode lastErrorCode,LastErrorMessage lastErrorMessage,LegacySourceType legacySourceType,LegacySourceId legacySourceId,CreatedAt createdAt,UpdatedAt updatedAt FROM c_PlatformAccounts WHERE Id=#{id} AND ArchivedAt IS NULL")
    Map<String,Object> findAccount(long id);

    @Select("SELECT Id id,Platform platform,AccountKind accountKind,DisplayName displayName,AccountHandle accountHandle,AdapterType adapterType,CAST(PublicConfigJson AS CHAR) publicConfigJson,Enabled enabled,ConnectionStatus connectionStatus,CredentialHint credentialHint,LastValidatedAt lastValidatedAt,LastConnectedAt lastConnectedAt,LastErrorCode lastErrorCode,LastErrorMessage lastErrorMessage,CreatedAt createdAt,UpdatedAt updatedAt FROM c_PlatformAccounts WHERE ArchivedAt IS NULL AND (#{query} IS NULL OR DisplayName LIKE CONCAT('%',#{query},'%') OR AccountHandle LIKE CONCAT('%',#{query},'%')) AND (#{platform} IS NULL OR Platform=#{platform}) AND (#{accountKind} IS NULL OR AccountKind=#{accountKind}) AND (#{status} IS NULL OR ConnectionStatus=#{status}) ORDER BY UpdatedAt DESC,Id DESC LIMIT #{offset},#{limit}")
    List<Map<String,Object>> findAccounts(@Param("query") String query,@Param("platform") String platform,@Param("accountKind") String accountKind,@Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM c_PlatformAccounts WHERE ArchivedAt IS NULL AND (#{query} IS NULL OR DisplayName LIKE CONCAT('%',#{query},'%') OR AccountHandle LIKE CONCAT('%',#{query},'%')) AND (#{platform} IS NULL OR Platform=#{platform}) AND (#{accountKind} IS NULL OR AccountKind=#{accountKind}) AND (#{status} IS NULL OR ConnectionStatus=#{status})")
    long countAccounts(@Param("query") String query,@Param("platform") String platform,@Param("accountKind") String accountKind,@Param("status") String status);

    @Select("SELECT Id id,AccountId accountId,SecretType secretType,EncryptedValue encryptedValue,SecretHint secretHint,SecretVersion secretVersion,LastValidatedAt lastValidatedAt FROM c_PlatformAccountSecrets WHERE AccountId=#{accountId} AND SecretType=#{secretType}")
    Map<String,Object> findSecret(@Param("accountId") long accountId,@Param("secretType") String secretType);

    @Select("SELECT SecretType secretType,SecretHint secretHint,SecretVersion secretVersion,LastValidatedAt lastValidatedAt FROM c_PlatformAccountSecrets WHERE AccountId=#{accountId} ORDER BY SecretType")
    List<Map<String,Object>> findSecretSummaries(long accountId);

    @Insert("INSERT INTO c_PlatformAccountSecrets(AccountId,SecretType,EncryptedValue,SecretHint,SecretVersion) VALUES(#{accountId},#{secretType},#{encryptedValue},#{secretHint},1)")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertSecret(SecretRecord record);

    @Update("UPDATE c_PlatformAccountSecrets SET EncryptedValue=#{encryptedValue},SecretHint=#{secretHint},SecretVersion=#{secretVersion},LastValidatedAt=NULL WHERE Id=#{id}")
    int updateSecret(@Param("id") long id,@Param("encryptedValue") String encryptedValue,@Param("secretHint") String secretHint,@Param("secretVersion") int secretVersion);

    @Update("UPDATE c_PlatformAccounts SET ConnectionStatus=#{status},CredentialHint=#{hint},LastValidatedAt=CASE WHEN #{validated}=TRUE THEN NOW(6) ELSE LastValidatedAt END,LastConnectedAt=CASE WHEN #{status}='CONNECTED' THEN NOW(6) ELSE LastConnectedAt END,LastErrorCode=#{errorCode},LastErrorMessage=#{errorMessage} WHERE Id=#{id} AND ArchivedAt IS NULL")
    int updateStatus(@Param("id") long id,@Param("status") String status,@Param("hint") String hint,@Param("validated") boolean validated,@Param("errorCode") String errorCode,@Param("errorMessage") String errorMessage);

    @Update("UPDATE c_PlatformAccounts SET Enabled=FALSE,ConnectionStatus='DISABLED',ArchivedAt=NOW(6) WHERE Id=#{id} AND ArchivedAt IS NULL")
    int archiveAccount(long id);

    @Select("SELECT 'TWITTER_PUBLISHING' consumerType,Id consumerId,Username consumerName FROM c_TwitterAccounts WHERE PlatformAccountId=#{id} UNION ALL SELECT 'CONTENT_COLLECTION',Id,DisplayName FROM c_ContentCollectionAccounts WHERE PlatformAccountId=#{id} AND ArchivedAt IS NULL UNION ALL SELECT 'CONTENT_PUBLISHING',Id,DisplayName FROM c_ContentAccounts WHERE PlatformAccountId=#{id} AND ArchivedAt IS NULL UNION ALL SELECT 'GEMINI_CONTENT',Id,'Gemini 内容生成' FROM c_ContentOperationSettings WHERE PlatformAccountId=#{id}")
    List<Map<String,Object>> findUsages(long id);

    @Select("SELECT Id id,Platform platform,AccountKind accountKind,DisplayName displayName,AccountHandle accountHandle,AdapterType adapterType,CAST(PublicConfigJson AS CHAR) publicConfigJson,Enabled enabled,ConnectionStatus connectionStatus,CredentialHint credentialHint,LegacySourceType legacySourceType,LegacySourceId legacySourceId FROM c_PlatformAccounts WHERE LegacySourceType=#{legacySourceType} AND LegacySourceId=#{legacySourceId} AND ArchivedAt IS NULL")
    Map<String,Object> findByLegacy(@Param("legacySourceType") String legacySourceType,@Param("legacySourceId") long legacySourceId);

    @Select("SELECT Id id,Username username,EncryptedStorageState encryptedStorageState,SessionStatus sessionStatus FROM c_TwitterAccounts")
    List<Map<String,Object>> findLegacyTwitterAccounts();

    @Select("SELECT Id id,DisplayName displayName,AdapterType adapterType,CredentialEncrypted credentialEncrypted,CredentialHint credentialHint,Enabled enabled FROM c_ContentCollectionAccounts WHERE ArchivedAt IS NULL")
    List<Map<String,Object>> findLegacyCollectionAccounts();

    @Select("SELECT Id id,DisplayName displayName,AccountHandle accountHandle,AdapterType adapterType,SessionEncrypted sessionEncrypted,SessionHint sessionHint,Enabled enabled,ConnectionStatus connectionStatus FROM c_ContentAccounts WHERE ArchivedAt IS NULL")
    List<Map<String,Object>> findLegacyContentAccounts();

    @Select("SELECT Id id,GeminiApiBaseUrl apiBaseUrl,GeminiApiKeyEncrypted apiKeyEncrypted,GeminiApiKeyHint apiKeyHint,AiGenerationEnabled enabled FROM c_ContentOperationSettings WHERE Id=1")
    List<Map<String,Object>> findLegacyGeminiConfigs();

    @Update("<script><choose><when test=\"legacySourceType == 'TWITTER_ACCOUNT'\">UPDATE c_TwitterAccounts SET PlatformAccountId=#{platformAccountId} WHERE Id=#{legacySourceId}</when><when test=\"legacySourceType == 'CONTENT_COLLECTION_ACCOUNT'\">UPDATE c_ContentCollectionAccounts SET PlatformAccountId=#{platformAccountId} WHERE Id=#{legacySourceId}</when><when test=\"legacySourceType == 'CONTENT_ACCOUNT'\">UPDATE c_ContentAccounts SET PlatformAccountId=#{platformAccountId} WHERE Id=#{legacySourceId}</when><when test=\"legacySourceType == 'CONTENT_GEMINI'\">UPDATE c_ContentOperationSettings SET PlatformAccountId=#{platformAccountId} WHERE Id=#{legacySourceId}</when><otherwise>SELECT 0</otherwise></choose></script>")
    int linkLegacyConsumer(@Param("legacySourceType") String legacySourceType,@Param("legacySourceId") long legacySourceId,@Param("platformAccountId") long platformAccountId);

    class AccountRecord {
        private Long id; private String platform; private String accountKind; private String displayName; private String accountHandle; private String adapterType; private String publicConfigJson; private boolean enabled=true; private String connectionStatus="NOT_CONFIGURED"; private String credentialHint; private String legacySourceType; private Long legacySourceId;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getPlatform(){return platform;} public void setPlatform(String v){platform=v;} public String getAccountKind(){return accountKind;} public void setAccountKind(String v){accountKind=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;} public String getAccountHandle(){return accountHandle;} public void setAccountHandle(String v){accountHandle=v;} public String getAdapterType(){return adapterType;} public void setAdapterType(String v){adapterType=v;} public String getPublicConfigJson(){return publicConfigJson;} public void setPublicConfigJson(String v){publicConfigJson=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public String getConnectionStatus(){return connectionStatus;} public void setConnectionStatus(String v){connectionStatus=v;} public String getCredentialHint(){return credentialHint;} public void setCredentialHint(String v){credentialHint=v;} public String getLegacySourceType(){return legacySourceType;} public void setLegacySourceType(String v){legacySourceType=v;} public Long getLegacySourceId(){return legacySourceId;} public void setLegacySourceId(Long v){legacySourceId=v;}
    }
    class SecretRecord {
        private Long id; private long accountId; private String secretType; private String encryptedValue; private String secretHint;
        public Long getId(){return id;} public void setId(Long v){id=v;} public long getAccountId(){return accountId;} public void setAccountId(long v){accountId=v;} public String getSecretType(){return secretType;} public void setSecretType(String v){secretType=v;} public String getEncryptedValue(){return encryptedValue;} public void setEncryptedValue(String v){encryptedValue=v;} public String getSecretHint(){return secretHint;} public void setSecretHint(String v){secretHint=v;}
    }
}
