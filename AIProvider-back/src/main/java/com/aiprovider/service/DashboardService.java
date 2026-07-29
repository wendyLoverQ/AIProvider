package com.aiprovider.service;

import com.aiprovider.repository.DashboardRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepo;

    public DashboardService(DashboardRepository dashboardRepo) {
        this.dashboardRepo = dashboardRepo;
    }

    public Map<String, Object> getOverview() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getOverview", new String[] {}, new Object[] {});
    Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalChatMessages", dashboardRepo.count("maid_ChatMessages"));
        stats.put("totalLlmCalls", dashboardRepo.count("maid_LlmCallLogs"));
        stats.put("totalLlmConversations", dashboardRepo.count("maid_LlmChatConversations"));
        stats.put("totalTimeRecords", dashboardRepo.count("maid_TimerRecords"));
        stats.put("totalAgentCalls", dashboardRepo.count("maid_AgentToolCalls"));
        stats.put("totalDesktopSnapshots", dashboardRepo.count("maid_DesktopContextSnapshots"));
        stats.put("totalBroadcasts", dashboardRepo.count("maid_ProactiveBroadcastTriggerLogs"));
        stats.put("totalNotebooks", dashboardRepo.count("maid_NotebookNotes"));
        stats.put("totalReminders", dashboardRepo.count("maid_Reminders"));

        Map<String, Object> llmAgg = dashboardRepo.llmAggregation();
        stats.put("totalPromptTokens", llmAgg.getOrDefault("totalPromptTokens", 0L));
        stats.put("totalCompletionTokens", llmAgg.getOrDefault("totalCompletionTokens", 0L));
        stats.put("totalTokens", llmAgg.getOrDefault("totalTokens", 0L));
        stats.put("totalDurationMs", llmAgg.getOrDefault("totalDurationMs", 0L));
        stats.put("modelCount", llmAgg.getOrDefault("modelCount", 0L));
        stats.put("providerCount", llmAgg.getOrDefault("providerCount", 0L));

        Map<String, Object> timeAgg = dashboardRepo.timeAggregation();
        stats.put("totalTrackedSeconds", timeAgg.getOrDefault("totalTrackedSeconds", 0L));
        stats.put("recordCount", timeAgg.getOrDefault("recordCount", 0L));
        stats.put("activeDays", timeAgg.getOrDefault("activeDays", 0L));

        Map<String, Object> agentAgg = dashboardRepo.agentStats();
        stats.put("agentSuccessCount", agentAgg.getOrDefault("successCount", 0L));
        stats.put("agentErrorCount", agentAgg.getOrDefault("errorCount", 0L));

        Map<String, Object> desktopAgg = dashboardRepo.desktopStats();
        stats.putAll(desktopAgg);

        stats.put("maidState", dashboardRepo.latestMaidState());

        return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getOverview", stats);
    }

    public List<Map<String, Object>> getLlmUsageDaily(int days) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getLlmUsageDaily", new String[] { "days" }, new Object[] { days });
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getLlmUsageDaily", dashboardRepo.llmUsageDaily(days));
    }

    public List<Map<String, Object>> getLlmModelStats() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getLlmModelStats", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getLlmModelStats", dashboardRepo.llmModelStats());
    }

    public List<Map<String, Object>> getTimeTrackingDaily(int days) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getTimeTrackingDaily", new String[] { "days" }, new Object[] { days });
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getTimeTrackingDaily", dashboardRepo.timeTrackingDaily(days));
    }

    public List<Map<String, Object>> getAgentToolUsage() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getAgentToolUsage", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getAgentToolUsage", dashboardRepo.agentToolUsage());
    }

    public List<Map<String, Object>> getDesktopAppUsage() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getDesktopAppUsage", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getDesktopAppUsage", dashboardRepo.desktopAppUsage());
    }

    public List<Map<String, Object>> getBroadcastStats() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getBroadcastStats", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getBroadcastStats", dashboardRepo.broadcastStats());
    }

    public List<Map<String, Object>> getRecentChats(int limit) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getRecentChats", new String[] { "limit" }, new Object[] { limit });
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getRecentChats", dashboardRepo.recentChats(limit));
    }

    public List<Map<String, Object>> getRecentLlmCalls(int limit) {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getRecentLlmCalls", new String[] { "limit" }, new Object[] { limit });
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getRecentLlmCalls", dashboardRepo.recentLlmCalls(limit));
    }

    public Map<String, Object> getChatStats() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.DashboardService.getChatStats", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.DashboardService.getChatStats", dashboardRepo.chatStats());
    }
}
