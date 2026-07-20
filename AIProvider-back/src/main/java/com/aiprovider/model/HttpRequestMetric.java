package com.aiprovider.model;

import java.time.LocalDateTime;

public class HttpRequestMetric {
    private Long id;
    private final String method;
    private final String route;
    private final int statusCode;
    private final long durationMs;
    private final LocalDateTime createdAt;

    public HttpRequestMetric(String method,String route,int statusCode,long durationMs,LocalDateTime createdAt){
        this.method=method;this.route=route;this.statusCode=statusCode;this.durationMs=durationMs;this.createdAt=createdAt;
    }
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public String getMethod(){return method;}
    public String getRoute(){return route;}
    public int getStatusCode(){return statusCode;}
    public long getDurationMs(){return durationMs;}
    public LocalDateTime getCreatedAt(){return createdAt;}
}
