package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface TwitterMapper {
    @Select("SELECT Id id, Username username, SessionStatus sessionStatus, LastLoginAt lastLoginAt, LastError lastError FROM c_TwitterAccounts ORDER BY UpdatedAt DESC")
    List<Map<String, Object>> findAccounts();

    @Select("SELECT Id id, PlatformAccountId platformAccountId, Username username, SessionStatus sessionStatus, LastLoginAt lastLoginAt, LastError lastError FROM c_TwitterAccounts WHERE Id=#{id}")
    Map<String, Object> findAccount(@Param("id") long id);

    @Insert("INSERT INTO c_TwitterAccounts(Username, EncryptedStorageState, SessionStatus, LastLoginAt, LastError) " +
            "VALUES(#{username}, #{encryptedStorageState}, 'CONNECTED', NOW(3), NULL) " +
            "ON DUPLICATE KEY UPDATE EncryptedStorageState=VALUES(EncryptedStorageState), SessionStatus='CONNECTED', LastLoginAt=NOW(3), LastError=NULL")
    void saveConnectedAccount(@Param("username") String username, @Param("encryptedStorageState") String encryptedStorageState);

    @Select("SELECT Id FROM c_TwitterAccounts WHERE Username=#{username}")
    Long findAccountIdByUsername(@Param("username") String username);

    @Insert("INSERT INTO c_TwitterAccounts(Username, SessionStatus, LastLoginAt, LastError) " +
            "VALUES(#{username}, #{status}, IF(#{status}='CONNECTED', NOW(3), NULL), NULL) " +
            "ON DUPLICATE KEY UPDATE SessionStatus=VALUES(SessionStatus), " +
            "LastLoginAt=IF(VALUES(SessionStatus)='CONNECTED', NOW(3), LastLoginAt), LastError=NULL")
    void saveClientAccount(@Param("username") String username, @Param("status") String status);

    @Update("UPDATE c_TwitterAccounts SET SessionStatus=#{status}, LastError=#{error} WHERE Id=#{id}")
    int updateAccountStatus(@Param("id") long id, @Param("status") String status, @Param("error") String error);

    @Insert("INSERT INTO c_TwitterPosts(AccountId, Content, Status, ScheduledAt, Source) VALUES(#{accountId}, #{content}, 'PENDING', #{scheduledAt}, #{source})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertPost(PostInsert post);

    @Insert({"<script>",
            "INSERT INTO c_TwitterPostMedia(PostId, AssetId, StoragePath, LocalPath, LocalSource, OriginalFileName, ContentType, FileSize, Sha256, SortOrder) VALUES",
            "<foreach collection='items' item='item' separator=','>(#{item.postId},#{item.assetId},#{item.storagePath},#{item.localPath},#{item.localSource},#{item.originalFileName},#{item.contentType},#{item.fileSize},#{item.sha256},#{item.sortOrder})</foreach>",
            "</script>"})
    void insertMediaBatch(@Param("items") List<MediaInsert> items);

    @Select("SELECT p.Id id, p.AccountId accountId, a.Username username, p.Content content, p.Status status, " +
            "p.TweetUrl tweetUrl, p.ErrorMessage errorMessage, p.AttemptCount attemptCount, p.ScheduledAt scheduledAt, p.Source source, p.SentAt sentAt, p.CreatedAt createdAt " +
            "FROM c_TwitterPosts p JOIN c_TwitterAccounts a ON a.Id=p.AccountId WHERE p.Id=#{id}")
    Map<String, Object> findPost(@Param("id") long id);

    @Select("SELECT Id id, PostId postId, AssetId assetId, StoragePath storagePath, LocalPath localPath, LocalSource localSource, OriginalFileName originalFileName, ContentType contentType, " +
            "FileSize fileSize, Sha256 sha256, SortOrder sortOrder FROM c_TwitterPostMedia WHERE PostId=#{postId} ORDER BY SortOrder")
    List<Map<String, Object>> findMedia(@Param("postId") long postId);

    @Select("SELECT Id id, PostId postId, AssetId assetId, StoragePath storagePath, LocalPath localPath, LocalSource localSource, OriginalFileName originalFileName, ContentType contentType, " +
            "FileSize fileSize, Sha256 sha256, SortOrder sortOrder FROM c_TwitterPostMedia WHERE PostId=#{postId} AND Id=#{id}")
    Map<String, Object> findMediaItem(@Param("postId") long postId, @Param("id") long id);

    @Select("SELECT p.Id id, p.AccountId accountId, a.Username username, p.Content content, p.Status status, " +
            "p.TweetUrl tweetUrl, p.ErrorMessage errorMessage, p.AttemptCount attemptCount, p.ScheduledAt scheduledAt, p.Source source, p.SentAt sentAt, p.CreatedAt createdAt " +
            "FROM c_TwitterPosts p JOIN c_TwitterAccounts a ON a.Id=p.AccountId ORDER BY p.CreatedAt DESC LIMIT #{limit}")
    List<Map<String, Object>> findPosts(@Param("limit") int limit);

    @Select("SELECT p.Id id, p.AccountId accountId, a.Username username, p.Content content, p.Status status, " +
            "p.TweetUrl tweetUrl, p.ErrorMessage errorMessage, p.AttemptCount attemptCount, p.ScheduledAt scheduledAt, p.Source source, p.SentAt sentAt, p.CreatedAt createdAt " +
            "FROM c_TwitterPosts p JOIN c_TwitterAccounts a ON a.Id=p.AccountId " +
            "WHERE p.Status='PENDING' AND p.AccountId=#{accountId} AND p.ScheduledAt <= NOW(3) ORDER BY p.ScheduledAt, p.CreatedAt LIMIT #{limit}")
    List<Map<String, Object>> findPendingPosts(@Param("accountId") long accountId, @Param("limit") int limit);

    @Update("UPDATE c_TwitterPosts SET Status='PROCESSING', ErrorMessage=NULL, AttemptCount=AttemptCount+1 " +
            "WHERE Id=#{id} AND Status='PENDING' AND ScheduledAt <= NOW(3)")
    int claimPost(@Param("id") long id);

    @Update("UPDATE c_TwitterPosts SET Status='SENT', TweetUrl=#{tweetUrl}, ErrorMessage=NULL, SentAt=NOW(3) WHERE Id=#{id} AND Status='PROCESSING'")
    int markPostSent(@Param("id") long id, @Param("tweetUrl") String tweetUrl);

    @Update("UPDATE c_TwitterPosts SET Status='FAILED', ErrorMessage=#{error} WHERE Id=#{id} AND Status='PROCESSING'")
    int markPostFailed(@Param("id") long id, @Param("error") String error);

    @Update("UPDATE c_TwitterPosts SET Status='PENDING', ErrorMessage=NULL WHERE Id=#{id} AND Status='FAILED'")
    int retryPost(@Param("id") long id);

    @Update("UPDATE c_TwitterPosts SET Status='CANCELLED', ErrorMessage=NULL WHERE Id=#{id} AND Status IN ('PENDING','FAILED')")
    int cancelPost(@Param("id") long id);

    @Update("UPDATE c_TwitterPosts SET Status='PENDING', ErrorMessage='服务重启，任务已重新排队' WHERE Status='PROCESSING'")
    int recoverProcessingPosts();

    @Update("UPDATE c_TwitterPosts SET Status='PENDING', ErrorMessage='本机发布租约超时，任务已重新排队' " +
            "WHERE Status='PROCESSING' AND UpdatedAt < DATE_SUB(NOW(3), INTERVAL 10 MINUTE)")
    int recoverStaleClientPosts();

    @Select("SELECT Id FROM c_TwitterPosts WHERE Status='PENDING' AND ScheduledAt <= NOW(3) ORDER BY ScheduledAt, CreatedAt")
    List<Long> findPendingPostIds();

    class PostInsert {
        private Long id;
        private Long accountId;
        private String content;
        private java.time.LocalDateTime scheduledAt;
        private String source;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public java.time.LocalDateTime getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(java.time.LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    class MediaInsert {
        private Long postId;
        private Long assetId;
        private String storagePath;
        private String localPath;
        private String localSource;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private String sha256;
        private Integer sortOrder;
        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        public String getLocalPath() { return localPath; }
        public void setLocalPath(String localPath) { this.localPath = localPath; }
        public String getLocalSource() { return localSource; }
        public void setLocalSource(String localSource) { this.localSource = localSource; }
        public String getOriginalFileName() { return originalFileName; }
        public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
