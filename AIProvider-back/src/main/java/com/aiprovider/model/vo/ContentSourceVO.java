package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentSourceVO {
    private final Long id; private final String name; private final String sourceType; private final String sourceUrl;
    private final int pollIntervalMinutes; private final boolean enabled; private final String lastStatus; private final LocalDateTime lastCollectedAt;
    public ContentSourceVO(Long id,String name,String sourceType,String sourceUrl,int pollIntervalMinutes,boolean enabled,String lastStatus,LocalDateTime lastCollectedAt){
        this.id=id;this.name=name;this.sourceType=sourceType;this.sourceUrl=sourceUrl;this.pollIntervalMinutes=pollIntervalMinutes;this.enabled=enabled;this.lastStatus=lastStatus;this.lastCollectedAt=lastCollectedAt;
    }
    public Long getId(){return id;} public String getName(){return name;} public String getSourceType(){return sourceType;}
    public String getSourceUrl(){return sourceUrl;} public int getPollIntervalMinutes(){return pollIntervalMinutes;} public boolean isEnabled(){return enabled;}
    public String getLastStatus(){return lastStatus;} public LocalDateTime getLastCollectedAt(){return lastCollectedAt;}
}
