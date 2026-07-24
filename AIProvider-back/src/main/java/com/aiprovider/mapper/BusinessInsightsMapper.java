package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessInsightsMapper {

    String ROLE_CALL_FILTER = "(" +
            "EXISTS (SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = l.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId})) " +
            "OR (l.Source = 'maid_ai_decision' AND EXISTS (SELECT 1 FROM maid_ProactiveBroadcastTriggerLogs p WHERE p.EventId = l.CorrelationId AND LOWER(p.RoleId) = LOWER(#{roleId}))) " +
            "OR (l.Source = 'character_card_template_generation' AND l.CorrelationId LIKE CONCAT('character_card_template_', #{roleId}, '_%')) " +
            "OR (l.Source IN ('lazy_voice_lines', 'agent_decision') AND l.UserPrompt LIKE CONCAT('%roleId=', #{roleId}, '%'))" +
            ")";

    @Select("SELECT COUNT(*) FROM `${tableName}`")
    Long count(@Param("tableName") String tableName);

    @Select("${sql}")
    List<Map<String, Object>> queryList(@Param("sql") String sql);

    @Select("SELECT LastRole, LastModel, LastVoiceId, LastInteractionAt, DisturbanceMode, " +
            "TtsStatus, OllamaStatus, LastLlmLatencyMs, LastTtsLatencyMs, UpdatedAt " +
            "FROM maid_AppRuntimeStates ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String, Object> runtimeState();

    @Select("SELECT `Value` FROM maid_AppSettings WHERE `Key` = 'voice_current_role_id' " +
            "ORDER BY UpdatedAt DESC LIMIT 1")
    String currentRoleId();

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

    @Select("SELECT RoleId, Name, VoiceName, RoleTitle, CardSummary, CardSchemaVersion, PreferredVoiceId, " +
            "ValidationStatus, ValidationMessage, LastValidatedAt, TemplateCardGeneratedAt, " +
            "TemplateCardIterationCount, TemplateCardGenerationStatus, TemplateCardGenerationMessage, " +
            "TemplateCardLastAttemptAt, UpdatedAt FROM maid_VoiceRoleCards " +
            "WHERE IsEnabled = 1 AND LOWER(RoleId) = LOWER(#{roleId}) ORDER BY UpdatedAt DESC LIMIT 1")
    Map<String, Object> maidRoleCard(@Param("roleId") String roleId);

    @Select("SELECT COUNT(*) AS LlmCallCount, COALESCE(SUM(l.PromptTokens), 0) AS InputTokens, " +
            "COALESCE(SUM(l.CompletionTokens), 0) AS OutputTokens, COALESCE(SUM(l.TotalTokens), 0) AS TotalTokens, " +
            "(SELECT COUNT(*) FROM maid_LlmChatConversations c WHERE LOWER(c.RoleId) = LOWER(#{roleId})) AS ConversationCount, " +
            "(SELECT COUNT(*) FROM maid_LlmChatMessages m WHERE EXISTS (SELECT 1 FROM maid_LlmChatConversations c WHERE c.ConversationId = m.ConversationId AND LOWER(c.RoleId) = LOWER(#{roleId}))) AS MessageCount, " +
            "(SELECT COUNT(*) FROM maid_ProactiveBroadcastTriggerLogs p WHERE LOWER(p.RoleId) = LOWER(#{roleId})) AS ProactiveDecisionCount, " +
            "(SELECT COALESCE(SUM(CASE WHEN p.Responded = 1 THEN 1 ELSE 0 END), 0) FROM maid_ProactiveBroadcastTriggerLogs p WHERE LOWER(p.RoleId) = LOWER(#{roleId})) AS ProactiveResponseCount, " +
            "(SELECT COALESCE(SUM(CASE WHEN p.Spoke = 1 THEN 1 ELSE 0 END), 0) FROM maid_ProactiveBroadcastTriggerLogs p WHERE LOWER(p.RoleId) = LOWER(#{roleId})) AS ProactiveSpokenCount, " +
            "(SELECT COUNT(*) FROM maid_VoiceTriggerLogs v WHERE LOWER(v.RoleId) = LOWER(#{roleId}) AND v.Played = 1) AS VoicePlayCount " +
            "FROM maid_LlmCallLogs l WHERE " + ROLE_CALL_FILTER)
    Map<String, Object> maidRoleSummary(@Param("roleId") String roleId);

    @Select("SELECT DATE(l.CreatedAt) AS day, COUNT(*) AS callCount, COALESCE(SUM(l.TotalTokens), 0) AS totalTokens " +
            "FROM maid_LlmCallLogs l WHERE l.CreatedAt >= DATE_SUB(NOW(), INTERVAL 14 DAY) AND " + ROLE_CALL_FILTER + " " +
            "GROUP BY DATE(l.CreatedAt) ORDER BY day")
    List<Map<String, Object>> maidRoleDaily(@Param("roleId") String roleId);

    @Select("SELECT l.Id, l.Source, CASE l.Source " +
            "WHEN 'online_chat' THEN '聊天回复' WHEN 'maid_ai_decision' THEN '主动 AI 决策' " +
            "WHEN 'lazy_voice_lines' THEN '缓存语音文案' WHEN 'agent_decision' THEN 'Agent 决策' " +
            "WHEN 'character_card_template_generation' THEN '角色卡生成与迭代' ELSE l.Source END AS SourceName, " +
            "l.Provider, l.Model, l.PromptTokens, l.CompletionTokens, l.TotalTokens, " +
            "l.DurationMs, l.ResponseStatusCode, l.Error, l.CreatedAt FROM maid_LlmCallLogs l WHERE " + ROLE_CALL_FILTER + " " +
            "ORDER BY l.CreatedAt DESC LIMIT 5")
    List<Map<String, Object>> maidRoleRecentCalls(@Param("roleId") String roleId);

    @Select("SELECT BusinessKey, DisplayName, Description, Provider, ModelKey, IsEnabled, UpdatedAt " +
            "FROM maid_LlmBusinessModelConfigs WHERE IsEnabled = 1 ORDER BY Id")
    List<Map<String, Object>> activeLlmBusinesses();
}
