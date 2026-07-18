package com.aiprovider.service;

import com.aiprovider.model.vo.MonitorSummaryVO;
import com.aiprovider.model.vo.MonitorPageVO;
import com.aiprovider.repository.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MonitorService {
    private static final Duration RESOURCE_TTL=Duration.ofSeconds(15), OVERVIEW_TTL=Duration.ofSeconds(30);
    private static final Pattern SECRET=Pattern.compile("(?i)(authorization|cookie|secret(?:id|key)?|api[-_ ]?key|bearer)\\s*[:=]\\s*[^,;\\s]+",Pattern.CASE_INSENSITIVE);
    private final ISystemResourceMonitor system; private final TencentTrafficService traffic; private final AwsCloudWatchTrafficService awsTraffic; private final HealthService health; private final MonitorRepository repository;
    private final String localProvider;
    private final ZoneId zone;
    private final Object resourceLock=new Object(), overviewLock=new Object();
    private volatile ResourceCache resourceCache; private volatile OverviewCache overviewCache;
    public MonitorService(ISystemResourceMonitor system,TencentTrafficService traffic,AwsCloudWatchTrafficService awsTraffic,HealthService health,MonitorRepository repository,@Value("${monitor.timezone:Asia/Shanghai}") String timezone,@Value("${monitor.local-provider:TENCENT}") String localProvider){this.system=system;this.traffic=traffic;this.awsTraffic=awsTraffic;this.health=health;this.repository=repository;this.zone=ZoneId.of(timezone);this.localProvider=localProvider;}

    public MonitorSummaryVO summary(){
        ResourceCache resources=resources(); HealthService.Snapshot state=health.check();
        MonitorSummaryVO.Traffic localTraffic="AWS".equalsIgnoreCase(localProvider)?awsTraffic.traffic():traffic.current();
        return new MonitorSummaryVO(new MonitorSummaryVO.Health(state.getStatus(),state.getCheckedAt()),OffsetDateTime.now(zone),resources.memory,resources.disk,localTraffic);
    }
    private ResourceCache resources(){
        Instant now=Instant.now(); ResourceCache value=resourceCache;
        if(value!=null&&Duration.between(value.at,now).compareTo(RESOURCE_TTL)<0)return value;
        synchronized(resourceLock){value=resourceCache;now=Instant.now();if(value!=null&&Duration.between(value.at,now).compareTo(RESOURCE_TTL)<0)return value;
            value=new ResourceCache(now,system.memory(),system.disk());resourceCache=value;return value;}
    }
    public Map<String,Object> overview(){
        Instant now=Instant.now();OverviewCache value=overviewCache;
        if(value!=null&&Duration.between(value.at,now).compareTo(OVERVIEW_TTL)<0)return value.data;
        synchronized(overviewLock){value=overviewCache;now=Instant.now();if(value!=null&&Duration.between(value.at,now).compareTo(OVERVIEW_TTL)<0)return value.data;
            LocalDateTime since=LocalDate.now(zone).atStartOfDay();Map<String,Object> data=new LinkedHashMap<>(repository.todayOverview(since));long total=number(data.get("totalRequests"));long success=number(data.get("successCount"));
            data.put("successRate",total==0?0d:Math.round(success*10000d/total)/100d);data.put("p95DurationMs",repository.todayP95(since));data.put("collectedAt",OffsetDateTime.now(zone));
            overviewCache=new OverviewCache(now,Collections.unmodifiableMap(data));return overviewCache.data;}
    }
    public List<Map<String,Object>> timeseries(String range){
        int hours=hours(range);List<Map<String,Object>> rows=repository.timeseries(hours);Map<String,Object> p95=new HashMap<>();
        for(Map<String,Object> row:repository.timeseriesP95(hours))p95.put(String.valueOf(row.get("bucket")),row.get("p95DurationMs"));
        for(Map<String,Object> row:rows){long total=number(row.get("totalRequests")),failed=number(row.get("failureCount"));row.put("errorRate",total==0?0d:Math.round(failed*10000d/total)/100d);row.put("p95DurationMs",p95.getOrDefault(String.valueOf(row.get("bucket")),0));row.put("totalTokens",number(row.get("inputTokens"))+number(row.get("outputTokens")));}
        return rows;
    }
    public Map<String,Object> providers(){Map<String,Object> result=new LinkedHashMap<>();result.put("selection",repository.selection());result.put("activity",repository.providerActivity());
        HealthService.Snapshot snapshot=health.check();result.put("dependencies",Collections.singletonList(dependency("database",snapshot.getStatus(),snapshot.getCheckedAt())));return result;}
    private Map<String,Object> dependency(String name,String status,OffsetDateTime checkedAt){Map<String,Object> item=new LinkedHashMap<>();item.put("name",name);item.put("status",status);item.put("checkedAt",checkedAt);return item;}
    public MonitorPageVO<Map<String,Object>> failures(String range,String provider,String model,int page,int pageSize){
        if(page<1)throw new IllegalArgumentException("page 必须大于等于 1");if(pageSize<1||pageSize>100)throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");int hours=hours(range);
        List<Map<String,Object>> items=repository.failures(hours,cleanFilter(provider),cleanFilter(model),pageSize,(page-1)*pageSize);
        for(Map<String,Object> item:items){String error=String.valueOf(item.getOrDefault("error",""));String sanitized=SECRET.matcher(error).replaceAll("$1=[REDACTED]");item.put("errorSummary",sanitized.length()>240?sanitized.substring(0,240)+"…":sanitized);item.remove("error");item.remove("id");}
        return new MonitorPageVO<>(items,repository.failureCount(hours,cleanFilter(provider),cleanFilter(model)),page,pageSize);
    }
    private static String cleanFilter(String value){return value==null?null:value.trim();}
    private static int hours(String range){if("24h".equals(range)||range==null)return 24;if("7d".equals(range))return 168;if("30d".equals(range))return 720;throw new IllegalArgumentException("range 仅支持 24h、7d、30d");}
    private static long number(Object value){return value instanceof Number?((Number)value).longValue():0;}
    private static class ResourceCache{final Instant at;final MonitorSummaryVO.Resource memory,disk;ResourceCache(Instant at,MonitorSummaryVO.Resource memory,MonitorSummaryVO.Resource disk){this.at=at;this.memory=memory;this.disk=disk;}}
    private static class OverviewCache{final Instant at;final Map<String,Object> data;OverviewCache(Instant at,Map<String,Object> data){this.at=at;this.data=data;}}
}
