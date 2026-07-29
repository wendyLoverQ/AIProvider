package com.aiprovider.service;

import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {
    private final DataSource dataSource;
    public HealthService(DataSource dataSource) { this.dataSource = dataSource; }
    public Snapshot check() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.HealthService.check", new String[] {}, new Object[] {});
    boolean database = false;
        try (Connection connection = dataSource.getConnection()) { database = connection.isValid(2); }
        catch (Exception ignored) { database = false; }
        return com.aiprovider.logging.BusinessOperationLogger.success("service.HealthService.check", new Snapshot(database ? "UP" : "DOWN", OffsetDateTime.now(), database));
    }
    public static class Snapshot {
        private final String status; private final OffsetDateTime checkedAt; private final boolean database;
        Snapshot(String status, OffsetDateTime checkedAt, boolean database) { this.status=status; this.checkedAt=checkedAt; this.database=database; }
        public String getStatus(){return status;} public OffsetDateTime getCheckedAt(){return checkedAt;}
        public Map<String,Object> asLegacyResponse() {
            Map<String,Object> result = new LinkedHashMap<>(); result.put("status", "UP".equals(status) ? "ok" : "error");
            result.put("checkedAt", checkedAt); result.put("dependencies", java.util.Collections.singletonMap("database", database ? "UP" : "DOWN")); return result;
        }
    }
}
