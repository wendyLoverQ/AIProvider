package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentPublicationVO {
    private final Long id; private final String title; private final String accountName; private final String publishMode;
    private final String status; private final int attemptCount; private final String errorMessage; private final LocalDateTime scheduledAt;
    public ContentPublicationVO(Long id,String title,String accountName,String publishMode,String status,int attemptCount,String errorMessage,LocalDateTime scheduledAt){
        this.id=id;this.title=title;this.accountName=accountName;this.publishMode=publishMode;this.status=status;this.attemptCount=attemptCount;this.errorMessage=errorMessage;this.scheduledAt=scheduledAt;
    }
    public Long getId(){return id;} public String getTitle(){return title;} public String getAccountName(){return accountName;}
    public String getPublishMode(){return publishMode;} public String getStatus(){return status;} public int getAttemptCount(){return attemptCount;}
    public String getErrorMessage(){return errorMessage;} public LocalDateTime getScheduledAt(){return scheduledAt;}
}
