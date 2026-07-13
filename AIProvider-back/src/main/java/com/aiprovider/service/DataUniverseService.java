package com.aiprovider.service;

import com.aiprovider.repository.DataUniverseRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataUniverseService {

    private final DataUniverseRepository dataUniverseRepo;

    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("核心状态", Arrays.asList("maid_MaidStates", "maid_AppSettings", "maid_AppRuntimeStates", "maid_UserProfiles", "maid_DisturbanceSettings", "maid_AiConversations"));
        GROUPS.put("AI 与对话", Arrays.asList("maid_ChatMessages", "maid_ChatCommandLaunchers", "maid_LlmCallLogs", "maid_LlmChatConversations", "maid_LlmChatMessages", "maid_LlmProviderSelections", "maid_AgentCapabilities", "maid_AgentToolCalls"));
        GROUPS.put("知识与计划", Arrays.asList("maid_NotebookNotes", "maid_NotebookAttachments", "maid_Reminders", "maid_ReminderLogs", "maid_TimerRecords"));
        GROUPS.put("感知与主动服务", Arrays.asList("maid_DesktopContextSnapshots", "maid_ProactiveBroadcastSourceSettings", "maid_ProactiveBroadcastTriggerLogs", "maid_ActionTagDefinitions"));
        GROUPS.put("语音宇宙", Arrays.asList("maid_VoiceAssets", "maid_VoiceCacheDedupeLogs", "maid_VoiceConversations", "maid_VoiceRoleAudioCaches", "maid_VoiceRoleBindings", "maid_VoiceRoleCards", "maid_VoiceRoleVoices", "maid_VoiceRoles", "maid_VoiceTriggerLogs"));
        GROUPS.put("视频与媒体", Arrays.asList("maid_VideoAlbumFolders", "maid_VideoAlbums", "maid_VideoItems", "maid_VideoPlaybackHistories", "maid_VideoSiteConfigs", "maid_VideoSubtitleBindings", "maid_VideoSubtitleFolders", "maid_VideoTagDefinitions", "maid_RemoteAuthors", "maid_RemoteDownloadTasks", "maid_RemotePlayHistories", "maid_RemoteVideoItems", "maid_RemoteVideoSettings"));
        GROUPS.put("私密保险箱", Arrays.asList("maid_VaultItems", "maid_VaultItemHistories"));
        GROUPS.put("数据字典", Arrays.asList("maid_DbColumnComments"));
    }

    private static final Set<String> TABLES = GROUPS.values().stream()
        .flatMap(Collection::stream).collect(Collectors.toSet());

    public DataUniverseService(DataUniverseRepository dataUniverseRepo) {
        this.dataUniverseRepo = dataUniverseRepo;
    }

    public Map<String, Object> getIndex() {
        List<Map<String, Object>> columns = dataUniverseRepo.getAllColumns();
        Map<String, List<Map<String, Object>>> byTable = columns.stream().collect(Collectors.groupingBy(
            x -> String.valueOf(x.get("TABLE_NAME")), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> groups = new ArrayList<>();
        long grandTotal = 0;
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            List<Map<String, Object>> tables = new ArrayList<>();
            long groupTotal = 0;
            for (String table : group.getValue()) {
                if (!byTable.containsKey(table)) continue;
                long count = dataUniverseRepo.countByTable(table);
                groupTotal += count;
                tables.add(new LinkedHashMap<String, Object>() {{
                    put("name", table); put("count", count);
                    put("columns", byTable.get(table));
                    put("columnCount", byTable.get(table).size());
                }});
            }
            grandTotal += groupTotal;
            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("name", group.getKey());
            groupMap.put("count", groupTotal);
            groupMap.put("tables", tables);
            groups.add(groupMap);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableCount", TABLES.size());
        result.put("recordCount", grandTotal);
        result.put("groups", groups);
        return result;
    }

    public Map<String, Object> getRows(String table, int page, int size) {
        requireTable(table);
        page = Math.max(0, page);
        size = Math.max(10, Math.min(100, size));

        List<Map<String, Object>> columns = dataUniverseRepo.getTableColumns(table);
        String order = columns.stream()
            .filter(x -> "PRI".equals(x.get("COLUMN_KEY")))
            .map(x -> "`" + x.get("COLUMN_NAME") + "` DESC")
            .findFirst().orElse("1");

        String select = columns.stream()
            .map(dataUniverseRepo::safeColumnExpression)
            .collect(Collectors.joining(", "));

        long total = dataUniverseRepo.countTable(table);
        List<Map<String, Object>> rows = dataUniverseRepo.findRows(table, select, order, size, page * size);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", table);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("pages", Math.max(1, (total + size - 1) / size));
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    private void requireTable(String table) {
        if (!TABLES.contains(table))
            throw new IllegalArgumentException("Unknown AI Maid business table: " + table);
    }
}
