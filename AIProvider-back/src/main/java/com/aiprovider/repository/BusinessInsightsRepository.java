package com.aiprovider.repository;

import com.aiprovider.mapper.BusinessInsightsMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class BusinessInsightsRepository {

    private final BusinessInsightsMapper insightsMapper;

    public BusinessInsightsRepository(BusinessInsightsMapper insightsMapper) {
        this.insightsMapper = insightsMapper;
    }

    public Map<String, Long> countAll(List<String> tables) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tables) {
            Long value = insightsMapper.count(table);
            counts.put(table, value == null ? 0 : value);
        }
        return counts;
    }

    public Map<String, Object> queryFirst(String sql) {
        List<Map<String, Object>> rows = insightsMapper.queryList(sql);
        return rows.isEmpty() ? Collections.<String, Object>emptyMap() : rows.get(0);
    }

    public List<Map<String, Object>> queryList(String sql) {
        return insightsMapper.queryList(sql);
    }

    public Map<String, Object> runtimeState() {
        Map<String, Object> state = insightsMapper.runtimeState();
        return state == null ? Collections.<String, Object>emptyMap() : state;
    }

    public String currentRoleId() {
        return insightsMapper.currentRoleId();
    }

    public List<Map<String, Object>> activeReminders() {
        return insightsMapper.activeReminders();
    }

    public List<Map<String, Object>> recentNotes() {
        return insightsMapper.recentNotes();
    }

    public List<Map<String, Object>> recentVoiceLogs() {
        return insightsMapper.recentVoiceLogs();
    }

    public List<Map<String, Object>> recentVideos() {
        return insightsMapper.recentVideos();
    }

    public List<Map<String, Object>> recentRemoteVideos() {
        return insightsMapper.recentRemoteVideos();
    }

    public List<Map<String, Object>> voiceRoles() {
        return insightsMapper.voiceRoles();
    }

    public Map<String, Object> currentMaidState() {
        Map<String, Object> state = insightsMapper.currentMaidState();
        return state == null ? Collections.<String, Object>emptyMap() : state;
    }

    public Map<String, Object> maidState(String roleId) {
        Map<String, Object> state = insightsMapper.maidState(roleId);
        return state == null ? Collections.<String, Object>emptyMap() : state;
    }

    public Map<String, Object> maidRoleCard(String roleId) {
        Map<String, Object> card = insightsMapper.maidRoleCard(roleId);
        return card == null ? Collections.<String, Object>emptyMap() : card;
    }

    public Map<String, Object> maidRoleSummary(String roleId) {
        Map<String, Object> summary = insightsMapper.maidRoleSummary(roleId);
        return summary == null ? Collections.<String, Object>emptyMap() : summary;
    }

    public List<Map<String, Object>> maidRoleDaily(String roleId) {
        return insightsMapper.maidRoleDaily(roleId);
    }

    public List<Map<String, Object>> maidRoleRecentCalls(String roleId) {
        return insightsMapper.maidRoleRecentCalls(roleId);
    }

    public List<Map<String, Object>> activeLlmBusinesses() {
        return insightsMapper.activeLlmBusinesses();
    }
}
