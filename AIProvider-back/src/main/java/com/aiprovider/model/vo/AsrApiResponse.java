package com.aiprovider.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsrApiResponse {
    private final boolean success;private final TranscriptionData data;private final ErrorBody error;
    private AsrApiResponse(boolean success,TranscriptionData data,ErrorBody error){this.success=success;this.data=data;this.error=error;}
    public static AsrApiResponse success(AsrRecordVO value){return new AsrApiResponse(true,new TranscriptionData(value),null);}
    public static AsrApiResponse failure(String code,String message,String requestId){return new AsrApiResponse(false,null,new ErrorBody(code,message,requestId));}
    public boolean isSuccess(){return success;}public TranscriptionData getData(){return data;}public ErrorBody getError(){return error;}
    public static class TranscriptionData {private final String recordId;private final String text;private final String characterId;private final String sessionId;private final String provider;private final String model;private final String language;private final Long audioDurationMs;private final Long processingTimeMs;private final LocalDateTime createdAt;private TranscriptionData(AsrRecordVO value){recordId=value.getRecordId();text=value.getRecognizedText();characterId=value.getCharacterId();sessionId=value.getSessionId();provider=value.getProvider();model=value.getModel();language=value.getLanguage();audioDurationMs=value.getAudioDurationMs();processingTimeMs=value.getProcessingTimeMs();createdAt=value.getCreatedAt();}public String getRecordId(){return recordId;}public String getText(){return text;}public String getCharacterId(){return characterId;}public String getSessionId(){return sessionId;}public String getProvider(){return provider;}public String getModel(){return model;}public String getLanguage(){return language;}public Long getAudioDurationMs(){return audioDurationMs;}public Long getProcessingTimeMs(){return processingTimeMs;}public LocalDateTime getCreatedAt(){return createdAt;}}
    public static class ErrorBody {private final String code;private final String message;private final String requestId;public ErrorBody(String code,String message,String requestId){this.code=code;this.message=message;this.requestId=requestId;}public String getCode(){return code;}public String getMessage(){return message;}public String getRequestId(){return requestId;}}
}
