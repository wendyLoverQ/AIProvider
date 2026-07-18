package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentOperationSettingsVO {
    private final boolean automationEnabled; private final String defaultPublishMode; private final int crawlIntervalMinutes;
    private final int commentIntervalMinutes; private final String contentModel; private final LocalDateTime updatedAt;
    public ContentOperationSettingsVO(boolean automationEnabled,String defaultPublishMode,int crawlIntervalMinutes,int commentIntervalMinutes,String contentModel,LocalDateTime updatedAt){
        this.automationEnabled=automationEnabled;this.defaultPublishMode=defaultPublishMode;this.crawlIntervalMinutes=crawlIntervalMinutes;this.commentIntervalMinutes=commentIntervalMinutes;this.contentModel=contentModel;this.updatedAt=updatedAt;
    }
    public boolean isAutomationEnabled(){return automationEnabled;} public String getDefaultPublishMode(){return defaultPublishMode;}
    public int getCrawlIntervalMinutes(){return crawlIntervalMinutes;} public int getCommentIntervalMinutes(){return commentIntervalMinutes;}
    public String getContentModel(){return contentModel;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
