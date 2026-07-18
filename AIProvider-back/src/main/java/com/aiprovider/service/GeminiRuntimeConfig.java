package com.aiprovider.service;

import java.math.BigDecimal;

class GeminiRuntimeConfig {
    final boolean enabled; final String apiBaseUrl; final String model; final String apiKey; final String contentRewritePrompt;
    final String commentReplyPrompt; final BigDecimal temperature; final int maxOutputTokens;
    GeminiRuntimeConfig(boolean enabled,String apiBaseUrl,String model,String apiKey,String contentRewritePrompt,String commentReplyPrompt,BigDecimal temperature,int maxOutputTokens){
        this.enabled=enabled;this.apiBaseUrl=apiBaseUrl;this.model=model;this.apiKey=apiKey;this.contentRewritePrompt=contentRewritePrompt;this.commentReplyPrompt=commentReplyPrompt;this.temperature=temperature;this.maxOutputTokens=maxOutputTokens;
    }
}
