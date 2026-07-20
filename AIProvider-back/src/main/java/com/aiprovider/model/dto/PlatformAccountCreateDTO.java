package com.aiprovider.model.dto;

public class PlatformAccountCreateDTO {
    private String platform;private String displayName;private String accountHandle;private String adapterType;private String publicConfigJson;private Boolean enabled;
    public String getPlatform(){return platform;}public void setPlatform(String v){platform=v;}public String getDisplayName(){return displayName;}public void setDisplayName(String v){displayName=v;}public String getAccountHandle(){return accountHandle;}public void setAccountHandle(String v){accountHandle=v;}public String getAdapterType(){return adapterType;}public void setAdapterType(String v){adapterType=v;}public String getPublicConfigJson(){return publicConfigJson;}public void setPublicConfigJson(String v){publicConfigJson=v;}public Boolean getEnabled(){return enabled;}public void setEnabled(Boolean v){enabled=v;}
}
