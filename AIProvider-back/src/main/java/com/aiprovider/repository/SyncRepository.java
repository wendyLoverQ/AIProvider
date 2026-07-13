package com.aiprovider.repository;

import com.aiprovider.mapper.SyncMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SyncRepository {

    private final SyncMapper syncMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, TableMetadata> metadataCache = new ConcurrentHashMap<>();

    public SyncRepository(SyncMapper syncMapper, JdbcTemplate jdbcTemplate) {
        this.syncMapper = syncMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertSyncRun(String deviceId, int receivedCount, int upsertedCount) {
        syncMapper.insertSyncRun(deviceId, receivedCount, upsertedCount);
    }

    public List<Map<String, Object>> recentSyncRuns() {
        return syncMapper.recentSyncRuns();
    }

    public void upsert(String table, JsonNode payload) {
        if (payload == null || !payload.isObject())
            throw new IllegalArgumentException("不支持的数据格式");

        TableMetadata metadata = metadataCache.computeIfAbsent(table, this::loadMetadata);
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        payload.fields().forEachRemaining(entry -> {
            String actual = metadata.columnsByLowerCase().get(entry.getKey().toLowerCase(Locale.ROOT));
            if (actual != null)
                values.put(actual, jdbcValue(entry.getValue(), metadata.binaryColumns().contains(actual)));
        });

        if (metadata.columnsByLowerCase().containsKey("userid"))
            values.put(metadata.columnsByLowerCase().get("userid"), 0L);

        if (values.isEmpty() || metadata.primaryKeys().stream().anyMatch(key -> !values.containsKey(key)))
            throw new IllegalArgumentException(table + " 缺少主键或有效字段");

        String columns = values.keySet().stream().map(SyncRepository::quote).collect(Collectors.joining(","));
        String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
        String updates = values.keySet().stream()
            .filter(c -> !metadata.primaryKeys().contains(c))
            .map(c -> quote(c) + "=VALUES(" + quote(c) + ")")
            .collect(Collectors.joining(","));

        if (updates.trim().isEmpty())
            updates = quote(metadata.primaryKeys().get(0)) + "=" + quote(metadata.primaryKeys().get(0));

        String sql = "INSERT INTO " + quote(table) + "(" + columns + ") VALUES(" + placeholders +
            ") ON DUPLICATE KEY UPDATE " + updates;
        jdbcTemplate.update(sql, values.values().toArray());
    }

    private TableMetadata loadMetadata(String table) {
        List<Map<String, Object>> rows = syncMapper.getTableColumns(table);
        if (rows.isEmpty())
            throw new IllegalArgumentException("服务器不存在业务表 " + table);

        Map<String, String> columns = new LinkedHashMap<>();
        List<String> primary = new ArrayList<>();
        Set<String> binary = new HashSet<>();

        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("COLUMN_NAME"));
            columns.put(name.toLowerCase(Locale.ROOT), name);
            if ("PRI".equals(String.valueOf(row.get("COLUMN_KEY"))))
                primary.add(name);
            String type = String.valueOf(row.get("DATA_TYPE")).toLowerCase(Locale.ROOT);
            if (type.contains("blob") || type.contains("binary"))
                binary.add(name);
        }

        if (primary.isEmpty())
            throw new IllegalStateException(table + " 没有主键");
        return new TableMetadata(columns, primary, binary);
    }

    private static Object jdbcValue(JsonNode node, boolean binary) {
        if (node == null || node.isNull()) return null;
        if (binary && node.isTextual()) return Base64.getDecoder().decode(node.asText());
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        if (node.isBinary()) try { return node.binaryValue(); } catch (Exception ignored) {}
        return node.asText();
    }

    private static String quote(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static class TableMetadata {
        private final Map<String, String> columnsByLowerCase;
        private final List<String> primaryKeys;
        private final Set<String> binaryColumns;

        TableMetadata(Map<String, String> columnsByLowerCase, List<String> primaryKeys, Set<String> binaryColumns) {
            this.columnsByLowerCase = columnsByLowerCase;
            this.primaryKeys = primaryKeys;
            this.binaryColumns = binaryColumns;
        }

        Map<String, String> columnsByLowerCase() { return columnsByLowerCase; }
        List<String> primaryKeys() { return primaryKeys; }
        Set<String> binaryColumns() { return binaryColumns; }
    }
}
