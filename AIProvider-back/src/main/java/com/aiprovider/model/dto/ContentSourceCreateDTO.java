package com.aiprovider.model.dto;

public class ContentSourceCreateDTO {
    private String name;
    private String sourceType;
    private String sourceUrl;
    private Integer pollIntervalMinutes;
    private String platform;
    private String externalUid;
    private String externalHandle;
    private String adapterType;
    private String accessToken;
    private Long collectionAccountId;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { this.sourceType = value; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) { this.sourceUrl = value; }
    public Integer getPollIntervalMinutes() { return pollIntervalMinutes; }
    public void setPollIntervalMinutes(Integer value) { this.pollIntervalMinutes = value; }
    public String getPlatform() { return platform; }
    public void setPlatform(String value) { this.platform = value; }
    public String getExternalUid() { return externalUid; }
    public void setExternalUid(String value) { this.externalUid = value; }
    public String getExternalHandle() { return externalHandle; }
    public void setExternalHandle(String value) { this.externalHandle = value; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String value) { this.adapterType = value; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String value) { this.accessToken = value; }
    public Long getCollectionAccountId(){return collectionAccountId;}
    public void setCollectionAccountId(Long value){this.collectionAccountId=value;}
}
