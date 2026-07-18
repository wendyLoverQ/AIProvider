package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentAccountVO {
    private final Long id; private final String platform; private final String displayName; private final String accountHandle;
    private final String publishMode; private final boolean enabled; private final String connectionStatus; private final String adapterStatus;
    private final String lastError; private final LocalDateTime lastPublishedAt;
    public ContentAccountVO(Long id, String platform, String displayName, String accountHandle, String publishMode, boolean enabled,
                            String connectionStatus, String adapterStatus, String lastError, LocalDateTime lastPublishedAt) {
        this.id=id; this.platform=platform; this.displayName=displayName; this.accountHandle=accountHandle; this.publishMode=publishMode;
        this.enabled=enabled; this.connectionStatus=connectionStatus; this.adapterStatus=adapterStatus; this.lastError=lastError; this.lastPublishedAt=lastPublishedAt;
    }
    public Long getId(){return id;} public String getPlatform(){return platform;} public String getDisplayName(){return displayName;}
    public String getAccountHandle(){return accountHandle;} public String getPublishMode(){return publishMode;} public boolean isEnabled(){return enabled;}
    public String getConnectionStatus(){return connectionStatus;} public String getAdapterStatus(){return adapterStatus;}
    public String getLastError(){return lastError;} public LocalDateTime getLastPublishedAt(){return lastPublishedAt;}
}
