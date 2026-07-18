package com.aiprovider.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ContentAiConfigVO {
    private final String provider; private final boolean enabled; private final boolean apiKeyConfigured; private final String apiKeyHint;
    private final String apiBaseUrl; private final String model; private final String contentRewritePrompt; private final String commentReplyPrompt;
    private final BigDecimal temperature; private final int maxOutputTokens; private final LocalDateTime updatedAt;
    public ContentAiConfigVO(String provider,boolean enabled,boolean apiKeyConfigured,String apiKeyHint,String apiBaseUrl,String model,String contentRewritePrompt,String commentReplyPrompt,BigDecimal temperature,int maxOutputTokens,LocalDateTime updatedAt){
        this.provider=provider;this.enabled=enabled;this.apiKeyConfigured=apiKeyConfigured;this.apiKeyHint=apiKeyHint;this.apiBaseUrl=apiBaseUrl;this.model=model;this.contentRewritePrompt=contentRewritePrompt;this.commentReplyPrompt=commentReplyPrompt;this.temperature=temperature;this.maxOutputTokens=maxOutputTokens;this.updatedAt=updatedAt;
    }
    public String getProvider(){return provider;} public boolean isEnabled(){return enabled;} public boolean isApiKeyConfigured(){return apiKeyConfigured;}
    public String getApiKeyHint(){return apiKeyHint;} public String getApiBaseUrl(){return apiBaseUrl;} public String getModel(){return model;}
    public String getContentRewritePrompt(){return contentRewritePrompt;} public String getCommentReplyPrompt(){return commentReplyPrompt;}
    public BigDecimal getTemperature(){return temperature;} public int getMaxOutputTokens(){return maxOutputTokens;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
