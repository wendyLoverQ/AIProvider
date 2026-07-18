package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentPublicationVO {
    private final Long id; private final String title; private final String accountName; private final String publishMode;
    private final String status; private final int attemptCount; private final String errorCode; private final String errorMessage; private final LocalDateTime scheduledAt; private final LocalDateTime startedAt; private final LocalDateTime publishedAt;
    public ContentPublicationVO(Long id,String title,String accountName,String publishMode,String status,int attemptCount,String errorCode,String errorMessage,LocalDateTime scheduledAt,LocalDateTime startedAt,LocalDateTime publishedAt){
        this.id=id;this.title=title;this.accountName=accountName;this.publishMode=publishMode;this.status=status;this.attemptCount=attemptCount;this.errorCode=errorCode;this.errorMessage=errorMessage;this.scheduledAt=scheduledAt;this.startedAt=startedAt;this.publishedAt=publishedAt;
    }
    public Long getId(){return id;} public String getTitle(){return title;} public String getAccountName(){return accountName;}
    public String getPublishMode(){return publishMode;} public String getStatus(){return status;} public int getAttemptCount(){return attemptCount;}
    public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;} public LocalDateTime getScheduledAt(){return scheduledAt;} public LocalDateTime getStartedAt(){return startedAt;} public LocalDateTime getPublishedAt(){return publishedAt;}
}
