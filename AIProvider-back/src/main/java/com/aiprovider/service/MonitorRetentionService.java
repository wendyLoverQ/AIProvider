package com.aiprovider.service;

import com.aiprovider.repository.MonitorRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorRetentionService {
    private static final Logger log=LogManager.getLogger(MonitorRetentionService.class);
    private final MonitorRepository repository; private final int retentionDays;
    public MonitorRetentionService(MonitorRepository repository,@Value("${monitor.detail-retention-days:30}") int retentionDays){this.repository=repository;this.retentionDays=Math.max(30,retentionDays);}
    @Scheduled(cron="0 25 3 * * *") @Transactional public void cleanup(){
        try {
            int requested=repository.countExpiredHttpRequests(retentionDays),deleted=repository.deleteExpiredHttpRequests(retentionDays);
            if(deleted==requested)log.info("HTTP request metric retention completed operation=delete_expired_http_requests retentionDays={} requestCount={} affectedRows={}",retentionDays,requested,deleted);
            else {log.warn("HTTP request metric retention mismatch operation=delete_expired_http_requests retentionDays={} requestCount={} affectedRows={}",retentionDays,requested,deleted);throw new IllegalStateException("HTTP request metric retention affected-row mismatch");}
            int llmRequested=repository.countExpired(retentionDays),llmDeleted=repository.deleteExpired(retentionDays);
            if(llmDeleted==llmRequested)log.info("AI monitor retention completed operation=delete_expired_llm_calls retentionDays={} requestCount={} affectedRows={}",retentionDays,llmRequested,llmDeleted);
            else {log.warn("AI monitor retention mismatch operation=delete_expired_llm_calls retentionDays={} requestCount={} affectedRows={}",retentionDays,llmRequested,llmDeleted);throw new IllegalStateException("AI monitor retention affected-row mismatch");}
        } catch(Exception exception){log.warn("Monitor retention cleanup failed operation=delete_expired_monitor_data code=DATABASE_CLEANUP_ERROR type={}",exception.getClass().getSimpleName());if(exception instanceof RuntimeException)throw (RuntimeException)exception;throw new IllegalStateException("Monitor retention cleanup failed",exception);}
    }
}
