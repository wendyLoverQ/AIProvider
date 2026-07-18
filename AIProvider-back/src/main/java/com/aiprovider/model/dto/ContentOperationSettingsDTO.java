package com.aiprovider.model.dto;

public class ContentOperationSettingsDTO {
    private Boolean automationEnabled;
    private String defaultPublishMode;
    private Integer crawlIntervalMinutes;
    private Integer commentIntervalMinutes;
    private String contentModel;
    public Boolean getAutomationEnabled() { return automationEnabled; }
    public void setAutomationEnabled(Boolean value) { this.automationEnabled = value; }
    public String getDefaultPublishMode() { return defaultPublishMode; }
    public void setDefaultPublishMode(String value) { this.defaultPublishMode = value; }
    public Integer getCrawlIntervalMinutes() { return crawlIntervalMinutes; }
    public void setCrawlIntervalMinutes(Integer value) { this.crawlIntervalMinutes = value; }
    public Integer getCommentIntervalMinutes() { return commentIntervalMinutes; }
    public void setCommentIntervalMinutes(Integer value) { this.commentIntervalMinutes = value; }
    public String getContentModel() { return contentModel; }
    public void setContentModel(String value) { this.contentModel = value; }
}
