package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public class V20__prefix_table_names extends BaseJavaMigration {
    private static final String[] MAID_TABLES = {
        "ActionTagDefinitions", "AgentCapabilities", "AgentToolCalls", "AiConversations",
        "AppRuntimeStates", "AppSettings", "ChatCommandLaunchers", "ChatMessages",
        "DbColumnComments", "DesktopContextSnapshots", "DisturbanceSettings", "LlmCallLogs",
        "LlmChatConversations", "LlmChatMessages", "LlmProviderSelections", "MaidStates",
        "NotebookAttachments", "NotebookNotes", "ProactiveBroadcastSourceSettings",
        "ProactiveBroadcastTriggerLogs", "ProactiveTriggerRules", "ProactiveTriggerStates",
        "ReminderLogs", "Reminders", "RemoteAuthors", "RemoteDownloadTasks", "RemotePlayHistories",
        "RemoteVideoItems", "RemoteVideoSettings", "TimerRecords", "UserProfiles", "VaultItemHistories",
        "VaultItems", "VideoAlbumFolders", "VideoAlbums", "VideoItems", "VideoPlaybackHistories",
        "VideoSiteConfigs", "VideoSubtitleBindings", "VideoSubtitleFolders", "VideoTagDefinitions",
        "VoiceAssets", "VoiceCacheDedupeLogs", "VoiceConversations", "VoiceRoleAudioCaches",
        "VoiceRoleBindings", "VoiceRoleCards", "VoiceRoleVoices", "VoiceRoles", "VoiceTriggerLogs"
    };

    private static final String[] PROVIDER_TABLES = {
        "ComfyParameterSchemes", "ComfyUiPresets", "ComfyUiTasks", "ComfyWorkflows",
        "GeneratedAssets", "SyncRecords", "SyncRuns", "TwitterAccounts", "TwitterPostMedia", "TwitterPosts"
    };

    @Override
    public void migrate(Context context) throws Exception {
        Map<String, String> renames = new LinkedHashMap<>();
        for (String table : MAID_TABLES) renames.put(table, "maid_" + table);
        for (String table : PROVIDER_TABLES) renames.put(table, "c_" + table);

        Connection connection = context.getConnection();
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            boolean sourceExists = tableExists(connection, rename.getKey());
            boolean targetExists = tableExists(connection, rename.getValue());
            if (!sourceExists && targetExists) continue;
            if (!sourceExists) continue;
            if (targetExists)
                throw new IllegalStateException("表名前缀迁移冲突：" + rename.getKey() + " 与 " + rename.getValue() + " 同时存在");
            try (Statement statement = connection.createStatement()) {
                statement.execute("RENAME TABLE `" + rename.getKey() + "` TO `" + rename.getValue() + "`");
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        }
    }
}
