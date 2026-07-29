package com.aiprovider.service;

import com.aiprovider.model.dto.SyncBatchDTO;
import com.aiprovider.model.vo.SyncResultVO;
import com.aiprovider.repository.SyncRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SyncService {

    private static final Set<String> ALLOWED_TABLES = new HashSet<>(Arrays.asList(
        "TimerRecords", "AppSettings", "AiConversations", "MaidStates", "VoiceTriggerLogs", "VoiceRoles",
        "VoiceRoleVoices", "VoiceAssets", "VoiceRoleAudioCaches", "VoiceRoleBindings", "VoiceRoleCards",
        "ProactiveTriggerRules", "ProactiveTriggerStates", "DisturbanceSettings", "UserProfiles", "AppRuntimeStates",
        "Reminders", "ReminderLogs", "ChatCommandLaunchers", "NotebookNotes", "NotebookAttachments", "ActionTagDefinitions",
        "DesktopContextSnapshots", "ProactiveBroadcastSourceSettings", "ProactiveBroadcastTriggerLogs", "LlmCallLogs",
        "DbColumnComments", "LlmChatConversations", "LlmChatMessages", "LlmSourcePrompts", "LlmBusinessModelConfigs",
        "VoiceCacheDedupeLogs", "LlmProviderSelections",
        "ChatMessages", "VoiceConversations", "AgentCapabilities", "AgentToolCalls", "VaultItems", "VaultItemHistories",
        "VideoItems", "VideoAlbums", "VideoTagDefinitions", "VideoSiteConfigs", "VideoPlaybackHistories", "VideoSubtitleBindings",
        "RemoteVideoItems", "RemoteDownloadTasks", "RemotePlayHistories", "RemoteAuthors", "RemoteVideoSettings"
    ));

    private final SyncRepository syncRepo;

    public SyncService(SyncRepository syncRepo) {
        this.syncRepo = syncRepo;
    }

    @Transactional
    public SyncResultVO processBusinessBatch(String deviceId,
                                              List<SyncBatchDTO.BusinessRecord> records) {
                                              com.aiprovider.logging.BusinessOperationLogger.start("service.SyncService.processBusinessBatch", new String[] { "deviceId", "records" }, new Object[] { deviceId, records });
                                              if (deviceId == null || deviceId.trim().isEmpty() || records == null || records.size() > 200) {
            throw new IllegalArgumentException("deviceId 必填，单批最多 200 条");
        }

        Map<String, Integer> savedByTable = new LinkedHashMap<>();
        for (SyncBatchDTO.BusinessRecord record : records) {
            if (!ALLOWED_TABLES.contains(record.getTable()))
                throw new IllegalArgumentException("不支持的业务表或数据格式");
            syncRepo.upsert("maid_" + record.getTable(), record.getPayload());
            savedByTable.merge(record.getTable(), 1, Integer::sum);
        }

        syncRepo.insertSyncRun(deviceId, records.size(), records.size());

        SyncResultVO vo = new SyncResultVO();
        vo.setSaved(records.size());
        vo.setTables(savedByTable);
        vo.setSyncedAt(Instant.now().toString());
        return com.aiprovider.logging.BusinessOperationLogger.success("service.SyncService.processBusinessBatch", vo);
    }

    public Map<String, Object> getStatus() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.SyncService.getStatus", new String[] {}, new Object[] {});
    return com.aiprovider.logging.BusinessOperationLogger.success("service.SyncService.getStatus", Collections.singletonMap("recentRuns", syncRepo.recentSyncRuns()));
    }
}
