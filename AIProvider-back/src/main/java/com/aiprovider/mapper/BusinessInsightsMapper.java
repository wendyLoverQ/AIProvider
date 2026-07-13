package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessInsightsMapper {

    @Select("SELECT COUNT(*) FROM `${tableName}`")
    Long count(@Param("tableName") String tableName);

    @Select("${sql}")
    List<Map<String, Object>> queryList(@Param("sql") String sql);

    @Select("SELECT LastRole, LastModel, LastVoiceId, LastInteractionAt, DisturbanceMode, " +
            "TtsStatus, OllamaStatus, LastLlmLatencyMs, LastTtsLatencyMs, UpdatedAt " +
            "FROM maid_AppRuntimeStates ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String, Object> runtimeState();

    @Select("SELECT Id, Title, Message, DueAt, NextDueAt, Enabled, AllowTts, LastTriggeredAt " +
            "FROM maid_Reminders WHERE Enabled = 1 ORDER BY COALESCE(NextDueAt, DueAt) ASC LIMIT 6")
    List<Map<String, Object>> activeReminders();

    @Select("SELECT Id, Title, LEFT(ContentPlainText, 160) AS Preview, IsPinned, CreatedAt, UpdatedAt " +
            "FROM maid_NotebookNotes WHERE IsDeleted = 0 ORDER BY IsPinned DESC, UpdatedAt DESC LIMIT 6")
    List<Map<String, Object>> recentNotes();

    @Select("SELECT Id, Source, RoleId, Category, Played, Reason, LEFT(Text, 100) AS Text, CreatedAt " +
            "FROM maid_VoiceTriggerLogs ORDER BY CreatedAt DESC LIMIT 8")
    List<Map<String, Object>> recentVoiceLogs();

    @Select("SELECT Id, Title, SourceType, DurationSeconds, LastPositionSeconds, IsFavorite, IsCompleted, " +
            "LastPlayedAt, CreatedAt FROM maid_VideoItems ORDER BY COALESCE(LastPlayedAt, CreatedAt) DESC LIMIT 6")
    List<Map<String, Object>> recentVideos();

    @Select("SELECT Id, SiteName, Title, AuthorName, DurationText, DownloadStatus, LastResolvedAt, CreatedAt " +
            "FROM maid_RemoteVideoItems ORDER BY COALESCE(LastResolvedAt, CreatedAt) DESC LIMIT 6")
    List<Map<String, Object>> recentRemoteVideos();

    @Select("SELECT RoleId, DisplayName, AvatarPath, IsEnabled, UpdatedAt " +
            "FROM maid_VoiceRoles WHERE IsEnabled = 1 ORDER BY SortOrder, DisplayName")
    List<Map<String, Object>> voiceRoles();

    @Select("SELECT MaidId, Name, Mood, Favorability, CompanionshipSeconds, InteractionCount, " +
            "LastInteractionTime, UpdatedAt FROM maid_MaidStates WHERE IsCurrent = 1 " +
            "ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String, Object> currentMaidState();

    @Select("SELECT MaidId, Name, Mood, Favorability, CompanionshipSeconds, InteractionCount, " +
            "LastInteractionTime, UpdatedAt FROM maid_MaidStates WHERE LOWER(MaidId) = LOWER(#{roleId}) " +
            "ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String, Object> maidState(@Param("roleId") String roleId);

    @Select("SELECT COUNT(*) AS ConversationCount, " +
            "COALESCE((SELECT COUNT(*) FROM maid_LlmCallLogs l WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))), 0) AS LlmCallCount, " +
            "COALESCE((SELECT SUM(l.PromptTokens) FROM maid_LlmCallLogs l WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))), 0) AS InputTokens, " +
            "COALESCE((SELECT SUM(l.CompletionTokens) FROM maid_LlmCallLogs l WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))), 0) AS OutputTokens, " +
            "COALESCE((SELECT SUM(l.TotalTokens) FROM maid_LlmCallLogs l WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))), 0) AS TotalTokens, " +
            "COALESCE((SELECT COUNT(*) FROM maid_LlmChatMessages m WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = m.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))), 0) AS MessageCount " +
            "FROM maid_LlmChatConversations WHERE LOWER(RoleId) = LOWER(#{roleId})")
    Map<String, Object> maidRoleSummary(@Param("roleId") String roleId);

    @Select("SELECT DATE(l.CreatedAt) AS day, COUNT(*) AS callCount, COALESCE(SUM(l.TotalTokens), 0) AS totalTokens " +
            "FROM maid_LlmCallLogs l WHERE l.CreatedAt >= DATE_SUB(NOW(), INTERVAL 14 DAY) AND EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId})) " +
            "GROUP BY DATE(l.CreatedAt) ORDER BY day")
    List<Map<String, Object>> maidRoleDaily(@Param("roleId") String roleId);

    @Select("SELECT l.Id, l.Provider, l.Model, l.PromptTokens, l.CompletionTokens, l.TotalTokens, " +
            "l.DurationMs, l.ResponseStatusCode, l.Error, l.CreatedAt FROM maid_LlmCallLogs l WHERE EXISTS " +
            "(SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId})) " +
            "ORDER BY l.CreatedAt DESC LIMIT 5")
    List<Map<String, Object>> maidRoleRecentCalls(@Param("roleId") String roleId);
}
