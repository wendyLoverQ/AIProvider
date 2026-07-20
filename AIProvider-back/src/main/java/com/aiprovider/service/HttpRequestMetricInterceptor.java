package com.aiprovider.service;

import com.aiprovider.repository.MonitorRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class HttpRequestMetricInterceptor implements HandlerInterceptor {
    private static final Logger log=LogManager.getLogger(HttpRequestMetricInterceptor.class);
    private static final String START_NANOS=HttpRequestMetricInterceptor.class.getName()+".startNanos";
    private final MonitorRepository repository;

    public HttpRequestMetricInterceptor(MonitorRepository repository){this.repository=repository;}

    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
        if(included(request))request.setAttribute(START_NANOS,System.nanoTime());
        return true;
    }

    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception){
        Object started=request.getAttribute(START_NANOS);if(!(started instanceof Long))return;
        long durationMs=Math.max(0,(System.nanoTime()-(Long)started)/1_000_000L);
        Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if(matched==null)return;
        String route=String.valueOf(matched);
        int status=exception!=null&&response.getStatus()<400?500:response.getStatus();
        try {
            repository.recordHttpRequest(request.getMethod(),route,status,durationMs);
        } catch(Exception failure) {
            log.warn("HTTP request metric insert failed operation=record_http_request route={} requestCount=1 affectedRows=0 type={}",route,failure.getClass().getSimpleName());
        }
    }

    private boolean included(HttpServletRequest request){
        String method=request.getMethod(),uri=request.getRequestURI();
        return !"OPTIONS".equalsIgnoreCase(method)&&uri!=null&&uri.startsWith("/api/")
            &&!uri.equals("/api/health")&&!uri.startsWith("/api/health/")
            &&!uri.equals("/api/monitor")&&!uri.startsWith("/api/monitor/");
    }
}
