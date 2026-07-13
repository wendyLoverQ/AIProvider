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

        return stats;
    }

    public List<Map<String, Object>> getLlmUsageDaily(int days) {
        return dashboardRepo.llmUsageDaily(days);
    }

    public List<Map<String, Object>> getLlmModelStats() {
        return dashboardRepo.llmModelStats();
    }

    public List<Map<String, Object>> getTimeTrackingDaily(int days) {
        return dashboardRepo.timeTrackingDaily(days);
    }

    public List<Map<String, Object>> getAgentToolUsage() {
        return dashboardRepo.agentToolUsage();
    }

    public List<Map<String, Object>> getDesktopAppUsage() {
        return dashboardRepo.desktopAppUsage();
    }

    public List<Map<String, Object>> getBroadcastStats() {
        return dashboardRepo.broadcastStats();
    }

    public List<Map<String, Object>> getRecentChats(int limit) {
        return dashboardRepo.recentChats(limit);
    }

    public List<Map<String, Object>> getRecentLlmCalls(int limit) {
        return dashboardRepo.recentLlmCalls(limit);
    }

    public Map<String, Object> getChatStats() {
        return dashboardRepo.chatStats();
    }
}
