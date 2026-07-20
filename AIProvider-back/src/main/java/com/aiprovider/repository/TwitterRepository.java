package com.aiprovider.repository;

import com.aiprovider.mapper.TwitterMapper;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class TwitterRepository {
    private final TwitterMapper mapper;
    public TwitterRepository(TwitterMapper mapper) { this.mapper = mapper; }

    public List<Map<String, Object>> findAccounts() { return mapper.findAccounts(); }
    public Map<String, Object> findAccount(long id) { return mapper.findAccount(id); }
    public long saveConnectedAccount(String username, String encryptedState) {
        mapper.saveConnectedAccount(username, encryptedState);
        return mapper.findAccountIdByUsername(username);
    }
    public long saveClientAccount(String username, String status) {
        mapper.saveClientAccount(username, status);
        return mapper.findAccountIdByUsername(username);
    }
    public void updateAccountStatus(long id, String status, String error) { mapper.updateAccountStatus(id, status, error); }
    public long insertPost(TwitterMapper.PostInsert post) { mapper.insertPost(post); return post.getId(); }
    public void insertMediaBatch(List<TwitterMapper.MediaInsert> items) { mapper.insertMediaBatch(items); }
    public Map<String, Object> findPost(long id) { return mapper.findPost(id); }
    public List<Map<String, Object>> findMedia(long postId) { return mapper.findMedia(postId); }
    public Map<String, Object> findMediaItem(long postId, long id) { return mapper.findMediaItem(postId, id); }
    public List<Map<String, Object>> findPosts(int limit) { return mapper.findPosts(limit); }
    public List<Map<String, Object>> findPendingPosts(long accountId, int limit) { return mapper.findPendingPosts(accountId, limit); }
    public boolean claimPost(long id) { return mapper.claimPost(id) > 0; }
    public boolean markPostSent(long id, String tweetUrl) { return mapper.markPostSent(id, tweetUrl) > 0; }
    public boolean markPostFailed(long id, String error) { return mapper.markPostFailed(id, error) > 0; }
    public boolean retryPost(long id) { return mapper.retryPost(id) > 0; }
    public boolean cancelPost(long id) { return mapper.cancelPost(id) > 0; }
    public void recoverProcessingPosts() { mapper.recoverProcessingPosts(); }
    public void recoverStaleClientPosts() { mapper.recoverStaleClientPosts(); }
    public List<Long> findPendingPostIds() { return mapper.findPendingPostIds(); }
}
