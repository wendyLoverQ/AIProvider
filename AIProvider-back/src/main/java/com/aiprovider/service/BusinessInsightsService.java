package com.aiprovider.service;

import com.aiprovider.repository.BusinessInsightsRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BusinessInsightsService {

    private final BusinessInsightsRepository insightsRepo;

    public BusinessInsightsService(BusinessInsightsRepository insightsRepo) {
        this.insightsRepo = insightsRepo;
    }

    public Map<String, Object> getCommand() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", getCounts());
        result.put("runtime", insightsRepo.runtimeState());
        result.put("currentRoleId", insightsRepo.currentRoleId());
        result.put("reminders", insightsRepo.activeReminders());
        result.put("notes", insightsRepo.recentNotes());
        result.put("voice", insightsRepo.recentVoiceLogs());
        result.put("videos", insightsRepo.recentVideos());
        result.put("remoteVideos", insightsRepo.recentRemoteVideos());
        List<Map<String, Object>> roles = insightsRepo.voiceRoles();
        for (Map<String, Object> role : roles) {
            Object roleId = role.get("RoleId");
            if (roleId == null) roleId = role.get("roleId");
            if (roleId != null) role.put("avatarUrl", "/api/maid/avatars/" + roleId);
        }
        result.put("voiceRoles", roles);
        result.put("currentMaid", insightsRepo.currentMaidState());
        return result;
    }

    public Map<String, Object> getMaidRole(String roleId) {
        String normalizedRoleId = roleId == null ? "" : roleId.trim();
        if (!normalizedRoleId.matches("[A-Za-z0-9_-]{1,96}")) {
            throw new IllegalArgumentException("无效的角色 ID");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", normalizedRoleId);
        result.put("state", insightsRepo.maidState(normalizedRoleId));
        result.put("card", insightsRepo.maidRoleCard(normalizedRoleId));
        result.put("summary", insightsRepo.maidRoleSummary(normalizedRoleId));
        result.put("daily", insightsRepo.maidRoleDaily(normalizedRoleId));
        result.put("recentCalls", insightsRepo.maidRoleRecentCalls(normalizedRoleId));
        result.put("businesses", insightsRepo.activeLlmBusinesses());
        return result;
    }

    private Map<String, Long> getCounts() {
        Map<String, Long> databaseCounts = insightsRepo.countAll(Arrays.asList(
            "maid_NotebookNotes", "maid_Reminders", "maid_VoiceConversations", "maid_VoiceTriggerLogs",
            "maid_VoiceRoles", "maid_VideoItems", "maid_RemoteVideoItems", "maid_AiConversations",
            "maid_ProactiveTriggerRules", "maid_ProactiveTriggerStates"
        ));
        Map<String, Long> result = new LinkedHashMap<>();
        databaseCounts.forEach((table, count) ->
            result.put(table.startsWith("maid_") ? table.substring("maid_".length()) : table, count)
        );
        return result;
    }
}
