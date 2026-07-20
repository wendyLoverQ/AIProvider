package com.aiprovider.repository;

import com.aiprovider.mapper.MonitorMapper;
import com.aiprovider.model.HttpRequestMetric;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Repository
public class MonitorRepository {
    private static final Logger log=LogManager.getLogger(MonitorRepository.class);
    private final MonitorMapper mapper;
    private final ZoneId zone;
    public MonitorRepository(MonitorMapper mapper,@Value("${monitor.timezone:Asia/Shanghai}") String timezone){this.mapper=mapper;this.zone=ZoneId.of(timezone);}
    public int recordHttpRequest(String method,String route,int statusCode,long durationMs){
        HttpRequestMetric metric=new HttpRequestMetric(method,route,statusCode,durationMs,LocalDateTime.now(zone));int affected=mapper.insertHttpRequest(metric);
        if(affected==1)log.info("HTTP request metric recorded operation=record_http_request metricId={} route={} requestCount=1 affectedRows=1",metric.getId(),route);
        else log.warn("HTTP request metric insert mismatch operation=record_http_request metricId={} route={} requestCount=1 affectedRows={}",metric.getId(),route,affected);
        return affected;
    }
    public Map<String,Object> todayOverview(java.time.LocalDateTime since){return mapper.todayOverview(since);}
    public long todayP95(java.time.LocalDateTime since){Long value=mapper.todayP95(since);return value==null?0:value;}
    public List<Map<String,Object>> timeseries(int hours){return mapper.timeseries(hours);}
    public List<Map<String,Object>> timeseriesP95(int hours){return mapper.timeseriesP95(hours);}
    public Map<String,Object> selection(){return mapper.providerSelection();}
    public List<Map<String,Object>> providerActivity(){return mapper.providerActivity();}
    public List<Map<String,Object>> failures(int hours,String provider,String model,int limit,int offset){return mapper.failures(hours,provider,model,limit,offset);}
    public long failureCount(int hours,String provider,String model){return mapper.failureCount(hours,provider,model);}
    public int deleteExpired(int days){return mapper.deleteExpired(days);}
    public int countExpired(int days){return mapper.countExpired(days);}
    public int countExpiredHttpRequests(int days){return mapper.countExpiredHttpRequests(days);}
    public int deleteExpiredHttpRequests(int days){return mapper.deleteExpiredHttpRequests(days);}
}
