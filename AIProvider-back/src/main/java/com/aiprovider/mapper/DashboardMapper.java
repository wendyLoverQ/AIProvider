package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM `${tableName}`")
    Long count(@Param("tableName") String tableName);

    @Select("SELECT COALESCE(SUM(PromptTokens), 0) AS totalPromptTokens, " +
            "COALESCE(SUM(CompletionTokens), 0) AS totalCompletionTokens, " +
            "COALESCE(SUM(TotalTokens), 0) AS totalTokens, " +
            "COALESCE(SUM(DurationMs), 0) AS totalDurationMs, " +
            "COUNT(DISTINCT Model) AS modelCount, " +
            "COUNT(DISTINCT Provider) AS providerCount " +
            "FROM maid_LlmCallLogs")
    Map<String, Object> llmAggregation();

    @Select("SELECT COALESCE(SUM(DurationSeconds), 0) AS totalTrackedSeconds, " +
            "COUNT(*) AS recordCount, " +
            "COUNT(DISTINCT DATE(SavedAt)) AS activeDays " +
            "FROM maid_TimerRecords")
    Map<String, Object> timeAggregation();

    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN Status = 'success' THEN 1 ELSE 0 END), 0) AS successCount, " +
            "COALESCE(SUM(CASE WHEN Status = 'error' THEN 1 ELSE 0 END), 0) AS errorCount " +
            "FROM maid_AgentToolCalls")
    Map<String, Object> agentStats();

    @Select("SELECT COUNT(DISTINCT DATE(CapturedAt)) AS activeDays, " +
            "COUNT(DISTINCT ForegroundProcessName) AS appCount " +
            "FROM maid_DesktopContextSnapshots")
    Map<String, Object> desktopStats();

    @Select("SELECT * FROM maid_MaidStates ORDER BY Id DESC LIMIT 1")
    Map<String, Object> latestMaidState();

    @Select("SELECT DATE(CreatedAt) AS day, COUNT(*) AS callCount, " +
            "COALESCE(SUM(PromptTokens), 0) AS promptTokens, " +
            "COALESCE(SUM(CompletionTokens), 0) AS completionTokens, " +
            "COALESCE(SUM(TotalTokens), 0) AS totalTokens, " +
            "COALESCE(AVG(DurationMs), 0) AS avgDurationMs " +
            "FROM maid_LlmCallLogs " +
            "WHERE CreatedAt >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(CreatedAt) ORDER BY day")
    List<Map<String, Object>> llmUsageDaily(@Param("days") int days);

    @Select("SELECT Model, Provider, COUNT(*) AS callCount, " +
            "COALESCE(SUM(TotalTokens), 0) AS totalTokens, " +
            "COALESCE(AVG(DurationMs), 0) AS avgDurationMs " +
            "FROM maid_LlmCallLogs GROUP BY Model, Provider ORDER BY callCount DESC")
    List<Map<String, Object>> llmModelStats();

    @Select("SELECT DATE(SavedAt) AS day, COUNT(*) AS recordCount, " +
            "COALESCE(SUM(DurationSeconds), 0) AS totalSeconds " +
            "FROM maid_TimerRecords " +
            "WHERE SavedAt >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(SavedAt) ORDER BY day")
    List<Map<String, Object>> timeTrackingDaily(@Param("days") int days);

    @Select("SELECT CapabilityName AS tool_name, COUNT(*) AS count, " +
            "COALESCE(SUM(CASE WHEN Status = 'success' THEN 1 ELSE 0 END), 0) AS successCount, " +
            "COALESCE(SUM(CASE WHEN Status = 'error' THEN 1 ELSE 0 END), 0) AS errorCount " +
            "FROM maid_AgentToolCalls GROUP BY CapabilityName ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> agentToolUsage();

    @Select("SELECT ForegroundProcessName AS app, COUNT(*) AS count, " +
            "COALESCE(SUM(IdleSeconds), 0) AS totalIdleSeconds " +
            "FROM maid_DesktopContextSnapshots " +
            "GROUP BY ForegroundProcessName ORDER BY count DESC LIMIT 15")
    List<Map<String, Object>> desktopAppUsage();

    @Select("SELECT EventType, COUNT(*) AS count, " +
            "COALESCE(SUM(Responded), 0) AS responded, " +
            "COALESCE(SUM(Spoke), 0) AS spoke " +
            "FROM maid_ProactiveBroadcastTriggerLogs GROUP BY EventType ORDER BY count DESC")
    List<Map<String, Object>> broadcastStats();

    @Select("SELECT Id, ConversationId, Role, LEFT(Content, 200) AS Content, " +
            "CharacterId, ModelName, CreatedAt " +
            "FROM maid_ChatMessages ORDER BY CreatedAt DESC LIMIT #{limit}")
    List<Map<String, Object>> recentChats(@Param("limit") int limit);

    @Select("SELECT Id, Model, Provider, PromptTokens, CompletionTokens, TotalTokens, " +
            "DurationMs, ResponseStatusCode, LEFT(UserPrompt, 100) AS UserPrompt, " +
            "CreatedAt, CompletedAt " +
            "FROM maid_LlmCallLogs ORDER BY CreatedAt DESC LIMIT #{limit}")
    List<Map<String, Object>> recentLlmCalls(@Param("limit") int limit);

    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN Role = 'user' THEN 1 ELSE 0 END), 0) AS userCount, " +
            "COALESCE(SUM(CASE WHEN Role = 'assistant' THEN 1 ELSE 0 END), 0) AS assistantCount, " +
            "COALESCE(SUM(CASE WHEN Role = 'system' THEN 1 ELSE 0 END), 0) AS systemCount, " +
            "COUNT(DISTINCT ConversationId) AS conversationCount, " +
            "COUNT(DISTINCT ModelName) AS modelCount " +
            "FROM maid_ChatMessages")
    Map<String, Object> chatStats();
}
