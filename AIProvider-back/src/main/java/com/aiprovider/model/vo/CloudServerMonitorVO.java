package com.aiprovider.model.vo;

import java.time.OffsetDateTime;

public class CloudServerMonitorVO {
    private final String provider;
    private final String displayName;
    private final String status;
    private final OffsetDateTime collectedAt;
    private final MonitorSummaryVO.Resource memory;
    private final MonitorSummaryVO.Resource disk;
    private final MonitorSummaryVO.Traffic traffic;
    private final Network network;
    private final Instance instance;

    public CloudServerMonitorVO(String provider, String displayName, String status, OffsetDateTime collectedAt,
                                MonitorSummaryVO.Resource memory, MonitorSummaryVO.Resource disk,
                                MonitorSummaryVO.Traffic traffic, Network network, Instance instance) {
        this.provider=provider; this.displayName=displayName; this.status=status; this.collectedAt=collectedAt;
        this.memory=memory; this.disk=disk; this.traffic=traffic; this.network=network; this.instance=instance;
    }
    public String getProvider(){return provider;} public String getDisplayName(){return displayName;}
    public String getStatus(){return status;} public OffsetDateTime getCollectedAt(){return collectedAt;}
    public MonitorSummaryVO.Resource getMemory(){return memory;} public MonitorSummaryVO.Resource getDisk(){return disk;}
    public MonitorSummaryVO.Traffic getTraffic(){return traffic;} public Network getNetwork(){return network;}
    public Instance getInstance(){return instance;}

    public static class Network {
        private final Long inboundBytesPerSecond, outboundBytesPerSecond, monthInboundBytes, monthOutboundBytes;
        private final String source, unavailableReason;
        private final OffsetDateTime sampledSince;
        private final boolean available;
        public Network(Long inboundBytesPerSecond, Long outboundBytesPerSecond, Long monthInboundBytes, Long monthOutboundBytes,
                       String source, OffsetDateTime sampledSince, boolean available, String unavailableReason) {
            this.inboundBytesPerSecond=inboundBytesPerSecond; this.outboundBytesPerSecond=outboundBytesPerSecond;
            this.monthInboundBytes=monthInboundBytes; this.monthOutboundBytes=monthOutboundBytes;
            this.source=source; this.sampledSince=sampledSince; this.available=available; this.unavailableReason=unavailableReason;
        }
        public Long getInboundBytesPerSecond(){return inboundBytesPerSecond;} public Long getOutboundBytesPerSecond(){return outboundBytesPerSecond;}
        public Long getMonthInboundBytes(){return monthInboundBytes;} public Long getMonthOutboundBytes(){return monthOutboundBytes;}
        public String getSource(){return source;} public OffsetDateTime getSampledSince(){return sampledSince;}
        public boolean isAvailable(){return available;} public String getUnavailableReason(){return unavailableReason;}
    }

    public static class Instance {
        private final String instanceId, instanceType, region, availabilityZone, publicIpv4, privateIpv4, amiId, operatingSystem;
        private final boolean awsApiAvailable;
        private final String awsApiStatus;
        public Instance(String instanceId,String instanceType,String region,String availabilityZone,String publicIpv4,
                        String privateIpv4,String amiId,String operatingSystem,boolean awsApiAvailable,String awsApiStatus) {
            this.instanceId=instanceId;this.instanceType=instanceType;this.region=region;this.availabilityZone=availabilityZone;
            this.publicIpv4=publicIpv4;this.privateIpv4=privateIpv4;this.amiId=amiId;this.operatingSystem=operatingSystem;
            this.awsApiAvailable=awsApiAvailable;this.awsApiStatus=awsApiStatus;
        }
        public String getInstanceId(){return instanceId;} public String getInstanceType(){return instanceType;}
        public String getRegion(){return region;} public String getAvailabilityZone(){return availabilityZone;}
        public String getPublicIpv4(){return publicIpv4;} public String getPrivateIpv4(){return privateIpv4;}
        public String getAmiId(){return amiId;} public String getOperatingSystem(){return operatingSystem;}
        public boolean isAwsApiAvailable(){return awsApiAvailable;} public String getAwsApiStatus(){return awsApiStatus;}
    }
}
