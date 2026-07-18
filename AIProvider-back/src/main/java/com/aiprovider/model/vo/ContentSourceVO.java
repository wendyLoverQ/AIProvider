package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentSourceVO {
    private final Long id; private final String platform; private final String name; private final String sourceType; private final String externalUid; private final String externalHandle; private final String adapterType; private final String sourceUrl;
    private final Long collectionAccountId; private final String collectionAccountName; private final boolean credentialConfigured; private final String credentialHint; private final int pollIntervalMinutes; private final int fetchLimit; private final boolean enabled; private final String lastStatus; private final LocalDateTime lastCollectedAt; private final LocalDateTime lastTestedAt;
    public ContentSourceVO(Long id,String platform,String name,String sourceType,String externalUid,String externalHandle,String adapterType,String sourceUrl,Long collectionAccountId,String collectionAccountName,boolean credentialConfigured,String credentialHint,int pollIntervalMinutes,int fetchLimit,boolean enabled,String lastStatus,LocalDateTime lastCollectedAt,LocalDateTime lastTestedAt){
        this.id=id;this.platform=platform;this.name=name;this.sourceType=sourceType;this.externalUid=externalUid;this.externalHandle=externalHandle;this.adapterType=adapterType;this.sourceUrl=sourceUrl;this.collectionAccountId=collectionAccountId;this.collectionAccountName=collectionAccountName;this.credentialConfigured=credentialConfigured;this.credentialHint=credentialHint;this.pollIntervalMinutes=pollIntervalMinutes;this.fetchLimit=fetchLimit;this.enabled=enabled;this.lastStatus=lastStatus;this.lastCollectedAt=lastCollectedAt;this.lastTestedAt=lastTestedAt;
    }
    public Long getId(){return id;} public String getPlatform(){return platform;} public String getName(){return name;} public String getSourceType(){return sourceType;} public String getExternalUid(){return externalUid;} public String getExternalHandle(){return externalHandle;} public String getAdapterType(){return adapterType;}
    public String getSourceUrl(){return sourceUrl;} public Long getCollectionAccountId(){return collectionAccountId;} public String getCollectionAccountName(){return collectionAccountName;} public boolean isCredentialConfigured(){return credentialConfigured;} public String getCredentialHint(){return credentialHint;} public int getPollIntervalMinutes(){return pollIntervalMinutes;} public int getFetchLimit(){return fetchLimit;} public boolean isEnabled(){return enabled;}
    public String getLastStatus(){return lastStatus;} public LocalDateTime getLastCollectedAt(){return lastCollectedAt;} public LocalDateTime getLastTestedAt(){return lastTestedAt;}
}
