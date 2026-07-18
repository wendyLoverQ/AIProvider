package com.aiprovider.service;

import com.aiprovider.model.vo.CloudServerMonitorVO;
import com.aiprovider.model.vo.MonitorSummaryVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class AwsCloudWatchTrafficService {
    private static final Logger log=LogManager.getLogger(AwsCloudWatchTrafficService.class);
    private static final Duration CACHE_TTL=Duration.ofMinutes(5);
    private final CloudWatchClient client;
    private final String instanceId;
    private final ZoneId zone;
    private final long monthlyQuotaBytes;
    private final Object lock=new Object();
    private volatile Cached cached;

    @Autowired
    public AwsCloudWatchTrafficService(@Value("${monitor.aws.region:ap-northeast-1}") String region,
                                       @Value("${monitor.aws.instance-id:}") String instanceId,
                                       @Value("${monitor.timezone:Asia/Shanghai}") String timezone,
                                       @Value("${monitor.aws.monthly-quota-bytes:100000000000}") long monthlyQuotaBytes) {
        this(CloudWatchClient.builder().region(Region.of(region)).credentialsProvider(DefaultCredentialsProvider.create())
            .httpClientBuilder(UrlConnectionHttpClient.builder()).overrideConfiguration(awsTimeouts()).build(),
            instanceId,ZoneId.of(timezone),monthlyQuotaBytes);
    }

    private static ClientOverrideConfiguration awsTimeouts() { return ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(10)).build(); }

    AwsCloudWatchTrafficService(CloudWatchClient client,String instanceId,ZoneId zone,long monthlyQuotaBytes) {
        this.client=client;this.instanceId=instanceId.trim();this.zone=zone;this.monthlyQuotaBytes=Math.max(1,monthlyQuotaBytes);
    }

    public Snapshot current() {
        Cached value=cached;Instant now=Instant.now();
        if(value!=null&&Duration.between(value.at,now).compareTo(CACHE_TTL)<0)return value.snapshot;
        synchronized(lock){value=cached;now=Instant.now();if(value!=null&&Duration.between(value.at,now).compareTo(CACHE_TTL)<0)return value.snapshot;
            Snapshot snapshot=fetch();cached=new Cached(now,snapshot);return snapshot;}
    }

    private Snapshot fetch() {
        if(instanceId.isEmpty())return Snapshot.unavailable("AWS_INSTANCE_ID_MISSING");
        try {
            ZonedDateTime now=ZonedDateTime.now(zone);Instant monthStart=now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant();
            Instant collectedAt=now.toInstant();long monthIn=sum("NetworkIn",monthStart,collectedAt,3600);
            long monthOut=sum("NetworkOut",monthStart,collectedAt,3600);Instant liveStart=collectedAt.minus(Duration.ofMinutes(30));
            long liveIn=sum("NetworkIn",liveStart,collectedAt,300);long liveOut=sum("NetworkOut",liveStart,collectedAt,300);
            return new Snapshot(true,liveIn/1800,liveOut/1800,monthIn,monthOut,monthlyQuotaBytes,
                monthStart.atZone(zone).toOffsetDateTime(),collectedAt.atZone(zone).toOffsetDateTime(),null);
        } catch(Exception exception) {
            log.warn("AWS CloudWatch traffic query failed code=CLOUDWATCH_API_FAILED type={}",exception.getClass().getSimpleName());
            return Snapshot.unavailable("CLOUDWATCH_API_FAILED");
        }
    }

    private long sum(String metric,Instant start,Instant end,int periodSeconds) {
        GetMetricStatisticsRequest request=GetMetricStatisticsRequest.builder().namespace("AWS/EC2").metricName(metric)
            .dimensions(Dimension.builder().name("InstanceId").value(instanceId).build()).startTime(start).endTime(end)
            .period(periodSeconds).statistics(Statistic.SUM).build();
        GetMetricStatisticsResponse response=client.getMetricStatistics(request);double total=response.datapoints().stream()
            .filter(point->point.sum()!=null).mapToDouble(point->point.sum()).sum();
        return total>=Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,Math.round(total));
    }

    public MonitorSummaryVO.Traffic traffic() {
        Snapshot value=current();if(!value.available)return MonitorSummaryVO.Traffic.unavailable(value.reason);
        OffsetDateTime end=value.periodStart.plusMonths(1);long remaining=Math.max(0,value.quotaBytes-value.monthOutboundBytes);
        long overflow=Math.max(0,value.monthOutboundBytes-value.quotaBytes);
        return new MonitorSummaryVO.Traffic(value.monthOutboundBytes,value.quotaBytes,remaining,overflow,value.periodStart,end,end,
            "CLOUDWATCH_API_AWS_100GB_FREE_DTO",true,false,value.collectedAt);
    }
    public CloudServerMonitorVO.Network network() { Snapshot value=current();return new CloudServerMonitorVO.Network(
        value.available?value.inboundBytesPerSecond:null,value.available?value.outboundBytesPerSecond:null,
        value.available?value.monthInboundBytes:null,value.available?value.monthOutboundBytes:null,"CLOUDWATCH_API",
        value.periodStart,value.available,value.reason); }

    public static class Snapshot {
        private final boolean available;private final long inboundBytesPerSecond,outboundBytesPerSecond,monthInboundBytes,monthOutboundBytes,quotaBytes;
        private final OffsetDateTime periodStart,collectedAt;private final String reason;
        Snapshot(boolean available,long inboundBytesPerSecond,long outboundBytesPerSecond,long monthInboundBytes,long monthOutboundBytes,
                 long quotaBytes,OffsetDateTime periodStart,OffsetDateTime collectedAt,String reason) {
            this.available=available;this.inboundBytesPerSecond=inboundBytesPerSecond;this.outboundBytesPerSecond=outboundBytesPerSecond;
            this.monthInboundBytes=monthInboundBytes;this.monthOutboundBytes=monthOutboundBytes;this.quotaBytes=quotaBytes;
            this.periodStart=periodStart;this.collectedAt=collectedAt;this.reason=reason;
        }
        static Snapshot unavailable(String reason){return new Snapshot(false,0,0,0,0,0,null,null,reason);}
        public boolean isAvailable(){return available;} public OffsetDateTime getCollectedAt(){return collectedAt;}
    }
    private static class Cached { final Instant at;final Snapshot snapshot;Cached(Instant at,Snapshot snapshot){this.at=at;this.snapshot=snapshot;} }
}
