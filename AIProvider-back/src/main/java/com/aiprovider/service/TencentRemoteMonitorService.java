package com.aiprovider.service;

import com.aiprovider.model.vo.MonitorSummaryVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

@Service
public class TencentRemoteMonitorService {
    private final RestTemplate http;
    private final String summaryUrl;

    public TencentRemoteMonitorService(@Value("${monitor.tencent.server-url:http://124.222.185.195}") String serverUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.http = new RestTemplate(factory);
        this.summaryUrl = serverUrl.replaceAll("/+$", "") + "/api/monitor/summary";
    }

    public Snapshot current() {
        try {
            JsonNode root = http.getForObject(summaryUrl, JsonNode.class);
            JsonNode data = root == null ? null : root.path("data");
            if (data == null || data.isMissingNode() || data.isNull()) return Snapshot.unavailable("INVALID_REMOTE_RESPONSE");
            return new Snapshot(
                text(data.path("health"), "status", "UNKNOWN"),
                time(data.path("health").path("checkedAt")),
                resource(data.path("memory")), resource(data.path("disk")), true, null
            );
        } catch (Exception exception) {
            return Snapshot.unavailable("TENCENT_MONITOR_OFFLINE");
        }
    }

    private static MonitorSummaryVO.Resource resource(JsonNode node) {
        boolean available = node.path("available").asBoolean(false);
        return new MonitorSummaryVO.Resource(available ? node.path("usedBytes").asLong() : null,
            available ? node.path("totalBytes").asLong() : null, available,
            available ? null : text(node, "unavailableReason", "REMOTE_RESOURCE_UNAVAILABLE"));
    }
    private static String text(JsonNode node, String name, String defaultValue) {
        String value = node.path(name).asText(""); return value.isBlank() ? defaultValue : value;
    }
    private static OffsetDateTime time(JsonNode node) { try { return OffsetDateTime.parse(node.asText()); } catch (Exception ignored) { return null; } }

    public static class Snapshot {
        private final String status, unavailableReason;
        private final OffsetDateTime checkedAt;
        private final MonitorSummaryVO.Resource memory, disk;
        private final boolean available;
        Snapshot(String status, OffsetDateTime checkedAt, MonitorSummaryVO.Resource memory, MonitorSummaryVO.Resource disk, boolean available, String unavailableReason) {
            this.status=status;this.checkedAt=checkedAt;this.memory=memory;this.disk=disk;this.available=available;this.unavailableReason=unavailableReason;
        }
        static Snapshot unavailable(String reason) { MonitorSummaryVO.Resource resource=new MonitorSummaryVO.Resource(null,null,false,reason);return new Snapshot("OFFLINE",null,resource,resource,false,reason); }
        public String getStatus(){return status;} public OffsetDateTime getCheckedAt(){return checkedAt;}
        public MonitorSummaryVO.Resource getMemory(){return memory;} public MonitorSummaryVO.Resource getDisk(){return disk;}
        public boolean isAvailable(){return available;} public String getUnavailableReason(){return unavailableReason;}
    }
}
