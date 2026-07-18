package com.aiprovider.model.dto;

public class ContentSourceCreateDTO {
    private String name;
    private String sourceType;
    private String sourceUrl;
    private Integer pollIntervalMinutes;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { this.sourceType = value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; }
    public Integer getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(Integer value) { this.pollIntervalMinutes = value; }
}
