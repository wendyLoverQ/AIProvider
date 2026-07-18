package com.aiprovider.model.vo;

public class ContentCollectionAccountVO {
    private final Long id; private final String platform; private final String displayName; private final String adapterType; private final boolean credentialConfigured; private final String credentialHint; private final boolean enabled;
    public ContentCollectionAccountVO(Long id,String platform,String displayName,String adapterType,boolean credentialConfigured,String credentialHint,boolean enabled){this.id=id;this.platform=platform;this.displayName=displayName;this.adapterType=adapterType;this.credentialConfigured=credentialConfigured;this.credentialHint=credentialHint;this.enabled=enabled;}
    public Long getId(){return id;} public String getPlatform(){return platform;} public String getDisplayName(){return displayName;} public String getAdapterType(){return adapterType;} public boolean isCredentialConfigured(){return credentialConfigured;} public String getCredentialHint(){return credentialHint;} public boolean isEnabled(){return enabled;}
}
