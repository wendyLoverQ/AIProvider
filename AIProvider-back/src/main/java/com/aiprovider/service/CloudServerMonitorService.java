package com.aiprovider.service;

import com.aiprovider.model.vo.CloudServerMonitorVO;
import com.aiprovider.model.vo.MonitorSummaryVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CloudServerMonitorService {
    private final ISystemResourceMonitor system;
    private final HealthService health;
    private final TencentTrafficService tencentTraffic;
    private final TencentRemoteMonitorService tencentRemote;
    private final AwsCloudWatchTrafficService awsTraffic;
    private final AwsInstanceMetadataService awsMetadata;
    private final ZoneId zone;
    private final String tencentInstanceId, tencentPublicIp;

    public CloudServerMonitorService(ISystemResourceMonitor system, HealthService health, TencentTrafficService tencentTraffic,
                                     TencentRemoteMonitorService tencentRemote, AwsCloudWatchTrafficService awsTraffic,
                                     AwsInstanceMetadataService awsMetadata,
                                     @Value("${monitor.timezone:Asia/Shanghai}") String timezone,
                                     @Value("${tencent-cloud.lighthouse-instance-id:}") String tencentInstanceId,
                                     @Value("${monitor.tencent.public-ip:124.222.185.195}") String tencentPublicIp) {
        this.system=system;this.health=health;this.tencentTraffic=tencentTraffic;this.tencentRemote=tencentRemote;
        this.awsTraffic=awsTraffic;this.awsMetadata=awsMetadata;this.zone=ZoneId.of(timezone);
        this.tencentInstanceId=tencentInstanceId;this.tencentPublicIp=tencentPublicIp;
    }

    public Map<String,CloudServerMonitorVO> current() {
        Map<String,CloudServerMonitorVO> result=new LinkedHashMap<>();
        result.put("tencent",tencent()); result.put("aws",aws()); return result;
    }

    private CloudServerMonitorVO aws() {
        HealthService.Snapshot state=health.check(); OffsetDateTime now=OffsetDateTime.now(zone);
        AwsCloudWatchTrafficService.Snapshot trafficState=awsTraffic.current();CloudServerMonitorVO.Instance metadata=awsMetadata.current();
        CloudServerMonitorVO.Instance instance=new CloudServerMonitorVO.Instance(metadata.getInstanceId(),metadata.getInstanceType(),metadata.getRegion(),
            metadata.getAvailabilityZone(),metadata.getPublicIpv4(),metadata.getPrivateIpv4(),metadata.getAmiId(),metadata.getOperatingSystem(),
            trafficState.isAvailable(),trafficState.isAvailable()?"CLOUDWATCH_API_AVAILABLE":"CLOUDWATCH_API_UNAVAILABLE");
        return new CloudServerMonitorVO("AWS","AWS 东京",state.getStatus(),now,system.memory(),system.disk(),
            awsTraffic.traffic(),awsTraffic.network(),instance);
    }
    private CloudServerMonitorVO tencent() {
        TencentRemoteMonitorService.Snapshot remote=tencentRemote.current(); OffsetDateTime now=OffsetDateTime.now(zone);
        CloudServerMonitorVO.Network network=new CloudServerMonitorVO.Network(null,null,null,null,"REMOTE_MONITOR",null,false,
            remote.isAvailable()?"REMOTE_NETWORK_METRICS_NOT_CONFIGURED":remote.getUnavailableReason());
        CloudServerMonitorVO.Instance instance=new CloudServerMonitorVO.Instance(tencentInstanceId,null,"ap-shanghai",null,
            tencentPublicIp,null,null,null,false,"NOT_APPLICABLE");
        MonitorSummaryVO.Traffic traffic=tencentTraffic.current();
        return new CloudServerMonitorVO("TENCENT","腾讯云",remote.getStatus(),now,remote.getMemory(),remote.getDisk(),traffic,network,instance);
    }
}
