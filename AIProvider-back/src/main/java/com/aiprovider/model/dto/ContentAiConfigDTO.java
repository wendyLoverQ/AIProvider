package com.aiprovider.model.dto;

import java.math.BigDecimal;

public class ContentAiConfigDTO {
    private Boolean enabled; private String apiKey; private String apiBaseUrl; private String model;
    private String contentRewritePrompt; private String commentReplyPrompt; private BigDecimal temperature; private Integer maxOutputTokens;
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public String getApiBaseUrl(){return apiBaseUrl;} public void setApiBaseUrl(String v){apiBaseUrl=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getContentRewritePrompt(){return contentRewritePrompt;} public void setContentRewritePrompt(String v){contentRewritePrompt=v;}
    public String getCommentReplyPrompt(){return commentReplyPrompt;} public void setCommentReplyPrompt(String v){commentReplyPrompt=v;}
    public BigDecimal getTemperature(){return temperature;} public void setTemperature(BigDecimal v){temperature=v;}
    public Integer getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(Integer v){maxOutputTokens=v;}
}
