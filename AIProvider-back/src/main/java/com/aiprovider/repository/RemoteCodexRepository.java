package com.aiprovider.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class RemoteCodexRepository {
    private final JdbcTemplate jdbc;
    public RemoteCodexRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public void create(String id,String title,LocalDateTime now){
        jdbc.update("INSERT INTO c_RemoteCodexConversations (Id,Title,Status,CreatedAt,UpdatedAt) VALUES (?,?,?,?,?)",id,title,"READY",Timestamp.valueOf(now),Timestamp.valueOf(now));
    }
    public List<Map<String,Object>> list(){return jdbc.queryForList(
        "SELECT c.Id id,c.CodexThreadId codexThreadId,c.Title title,c.Status status,c.ErrorMessage errorMessage,c.CreatedAt createdAt,c.UpdatedAt updatedAt,COUNT(m.Id) messageCount " +
        "FROM c_RemoteCodexConversations c LEFT JOIN c_RemoteCodexMessages m ON m.ConversationId=c.Id GROUP BY c.Id,c.CodexThreadId,c.Title,c.Status,c.ErrorMessage,c.CreatedAt,c.UpdatedAt ORDER BY c.UpdatedAt DESC LIMIT 100");}
    public Map<String,Object> get(String id){List<Map<String,Object>> rows=jdbc.queryForList(
        "SELECT Id id,CodexThreadId codexThreadId,Title title,Status status,ErrorMessage errorMessage,CreatedAt createdAt,UpdatedAt updatedAt FROM c_RemoteCodexConversations WHERE Id=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("远程 Codex 对话不存在");return rows.get(0);}
    public List<Map<String,Object>> messages(String id){return jdbc.queryForList(
        "SELECT Id id,Role role,Content content,CreatedAt createdAt FROM c_RemoteCodexMessages WHERE ConversationId=? ORDER BY Id",id);}
    public void message(String id,String role,String content,LocalDateTime now){jdbc.update(
        "INSERT INTO c_RemoteCodexMessages (ConversationId,Role,Content,CreatedAt) VALUES (?,?,?,?)",id,role,content,Timestamp.valueOf(now));
        jdbc.update("UPDATE c_RemoteCodexConversations SET UpdatedAt=? WHERE Id=?",Timestamp.valueOf(now),id);}
    public void running(String id,LocalDateTime now){jdbc.update("UPDATE c_RemoteCodexConversations SET Status='RUNNING',ErrorMessage=NULL,UpdatedAt=? WHERE Id=?",Timestamp.valueOf(now),id);}
    public void completed(String id,String threadId,LocalDateTime now){jdbc.update(
        "UPDATE c_RemoteCodexConversations SET CodexThreadId=COALESCE(?,CodexThreadId),Status='READY',ErrorMessage=NULL,UpdatedAt=? WHERE Id=?",threadId,Timestamp.valueOf(now),id);}
    public void failed(String id,String error,LocalDateTime now){jdbc.update(
        "UPDATE c_RemoteCodexConversations SET Status='ERROR',ErrorMessage=?,UpdatedAt=? WHERE Id=?",error,Timestamp.valueOf(now),id);}
}
