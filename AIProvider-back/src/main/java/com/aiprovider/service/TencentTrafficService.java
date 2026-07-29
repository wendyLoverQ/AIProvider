package com.aiprovider.service;

import com.aiprovider.model.vo.MonitorSummaryVO;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.lighthouse.v20200324.LighthouseClient;
import com.tencentcloudapi.lighthouse.v20200324.models.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class TencentTrafficService {
    private static final Logger log = LogManager.getLogger(TencentTrafficService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration STALE_LIMIT = Duration.ofHours(1);
    private final String secretId, secretKey, region, instanceId;
    private final Object lock = new Object();
    private volatile Cache cache;

    public TencentTrafficService(@Value("${tencent-cloud.secret-id:}") String secretId,
                                 @Value("${tencent-cloud.secret-key:}") String secretKey,
                                 @Value("${tencent-cloud.region:ap-shanghai}") String region,
                                 @Value("${tencent-cloud.lighthouse-instance-id:}") String instanceId) {
        this.secretId=secretId; this.secretKey=secretKey; this.region=region; this.instanceId=instanceId;
    }

    public MonitorSummaryVO.Traffic current() {
    com.aiprovider.logging.BusinessOperationLogger.start("service.TencentTrafficService.current", new String[] {}, new Object[] {});
    Instant now = Instant.now(); Cache snapshot = cache;
        if (snapshot != null && Duration.between(snapshot.fetchedAt, now).compareTo(CACHE_TTL) < 0) return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", snapshot.value);
        synchronized (lock) {
            snapshot = cache; now = Instant.now();
            if (snapshot != null && Duration.between(snapshot.fetchedAt, now).compareTo(CACHE_TTL) < 0) return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", snapshot.value);
            if (secretId.trim().isEmpty() || secretKey.trim().isEmpty()) return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", MonitorSummaryVO.Traffic.unavailable("NOT_CONFIGURED"));
            try {
                MonitorSummaryVO.Traffic value = fetch(); cache = new Cache(now, value); return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", value);
            } catch (TencentCloudSDKException exception) {
                log.warn("Tencent Cloud call failed action=DescribeInstancesTrafficPackages code={} requestId={} occurredAt={}",
                    exception.getErrorCode(), exception.getRequestId(), OffsetDateTime.now());
            } catch (RuntimeException exception) {
                log.warn("Tencent Cloud call failed action=DescribeInstancesTrafficPackages code=LOCAL_RESPONSE_ERROR requestId=none occurredAt={}", OffsetDateTime.now());
            }
            if (snapshot != null && Duration.between(snapshot.fetchedAt, now).compareTo(STALE_LIMIT) < 0) return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", snapshot.value.asStale());
            return com.aiprovider.logging.BusinessOperationLogger.success("service.TencentTrafficService.current", MonitorSummaryVO.Traffic.unavailable("UNAVAILABLE"));
        }
    }

    private MonitorSummaryVO.Traffic fetch() throws TencentCloudSDKException {
        LighthouseClient client = new LighthouseClient(new Credential(secretId, secretKey), region);
        DescribeInstancesTrafficPackagesRequest request = new DescribeInstancesTrafficPackagesRequest();
        request.setInstanceIds(new String[]{instanceId});
        DescribeInstancesTrafficPackagesResponse response = client.DescribeInstancesTrafficPackages(request);
        List<TrafficPackage> packages = new ArrayList<>();
        if (response.getInstanceTrafficPackageSet() != null) for (InstanceTrafficPackage instance : response.getInstanceTrafficPackageSet()) {
            if (instanceId.equals(instance.getInstanceId()) && instance.getTrafficPackageSet() != null) packages.addAll(Arrays.asList(instance.getTrafficPackageSet()));
        }
        TrafficPackage selected = selectCurrent(packages, Instant.now());
        if (selected == null) {
            log.warn("Tencent Cloud call failed action=DescribeInstancesTrafficPackages code=NO_ACTIVE_PACKAGE requestId={} occurredAt={}", response.getRequestId(), OffsetDateTime.now());
            return MonitorSummaryVO.Traffic.unavailable("NO_ACTIVE_PACKAGE");
        }
        OffsetDateTime collected = OffsetDateTime.now();
        return new MonitorSummaryVO.Traffic(selected.getTrafficUsed(), selected.getTrafficPackageTotal(), selected.getTrafficPackageRemaining(),
            selected.getTrafficOverflow(), parse(selected.getStartTime()), parse(selected.getEndTime()), parse(selected.getDeadline()),
            selected.getStatus(), true, false, collected);
    }

    static TrafficPackage selectCurrent(List<TrafficPackage> packages, Instant now) {
        return packages.stream().filter(Objects::nonNull).filter(item -> {
            OffsetDateTime start=parse(item.getStartTime()), end=parse(item.getEndTime());
            return start != null && end != null && !now.isBefore(start.toInstant()) && !now.isAfter(end.toInstant());
        }).sorted(Comparator.comparing((TrafficPackage item) -> !"NETWORK_NORMAL".equals(item.getStatus()))
            .thenComparing(item -> parse(item.getEndTime()), Comparator.nullsLast(Comparator.reverseOrder()))).findFirst().orElse(null);
    }
    private static OffsetDateTime parse(String value) { try { return value == null ? null : OffsetDateTime.parse(value); } catch (DateTimeParseException ignored) { return null; } }
    private static class Cache { private final Instant fetchedAt; private final MonitorSummaryVO.Traffic value; Cache(Instant fetchedAt, MonitorSummaryVO.Traffic value){this.fetchedAt=fetchedAt;this.value=value;} }
}
