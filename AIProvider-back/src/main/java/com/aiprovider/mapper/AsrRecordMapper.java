package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AsrRecordMapper {
    @Insert("INSERT INTO c_AsrTranscriptionRecords(RequestId,CharacterId,CharacterNameSnapshot,SessionId,AudioPath,AudioFormat,AudioSize,Provider,Model,Language,Status) VALUES(#{requestId},#{characterId},#{characterNameSnapshot},#{sessionId},#{audioPath},#{audioFormat},#{audioSize},#{provider},#{model},#{language},'PROCESSING')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Record row);

    @Update("UPDATE c_AsrTranscriptionRecords SET RecordId=#{recordId} WHERE Id=#{id} AND RecordId IS NULL")
    int assignRecordId(@Param("id") long id,@Param("recordId") String recordId);

    @Update("UPDATE c_AsrTranscriptionRecords SET RecognizedText=#{text},AudioDurationMs=#{durationMs},ProcessingTimeMs=#{processingMs},RateLimitRequestsLimit=#{requestLimit},RateLimitRequestsRemaining=#{requestsRemaining},RateLimitRequestsResetAfter=#{requestsResetAfter},RateLimitCapturedAt=CASE WHEN #{requestLimit} IS NOT NULL AND #{requestsRemaining} IS NOT NULL THEN CURRENT_TIMESTAMP(6) ELSE NULL END,Status='SUCCESS',ErrorCode=NULL,ErrorMessage=NULL WHERE Id=#{id} AND Status='PROCESSING'")
    int markSuccess(@Param("id") long id,@Param("text") String text,@Param("durationMs") Long durationMs,@Param("processingMs") long processingMs,@Param("requestLimit") Long requestLimit,@Param("requestsRemaining") Long requestsRemaining,@Param("requestsResetAfter") String requestsResetAfter);

    @Update("UPDATE c_AsrTranscriptionRecords SET ProcessingTimeMs=#{processingMs},Status='FAILED',ErrorCode=#{errorCode},ErrorMessage=#{errorMessage} WHERE Id=#{id} AND Status='PROCESSING'")
    int markFailed(@Param("id") long id,@Param("processingMs") long processingMs,@Param("errorCode") String errorCode,@Param("errorMessage") String errorMessage);

    @Update("UPDATE c_AsrTranscriptionRecords SET CorrectedText=#{correctedText} WHERE RecordId=#{recordId}")
    int updateCorrection(@Param("recordId") String recordId,@Param("correctedText") String correctedText);

    @Select("SELECT Name FROM maid_VoiceRoleCards WHERE LOWER(RoleId)=LOWER(#{characterId}) AND IsEnabled=1 ORDER BY UpdatedAt DESC LIMIT 1")
    String findCharacterName(String characterId);

    @Select("SELECT Id id,RecordId recordId,RequestId requestId,CharacterId characterId,CharacterNameSnapshot characterNameSnapshot,SessionId sessionId,AudioPath audioPath,AudioFormat audioFormat,AudioSize audioSize,AudioDurationMs audioDurationMs,RecognizedText recognizedText,CorrectedText correctedText,Provider provider,Model model,Language language,ProcessingTimeMs processingTimeMs,Status status,ErrorCode errorCode,ErrorMessage errorMessage,CreatedAt createdAt FROM c_AsrTranscriptionRecords WHERE RequestId=#{requestId}")
    Map<String,Object> findByRequestId(String requestId);

    @Select("SELECT Id id,RecordId recordId,RequestId requestId,CharacterId characterId,CharacterNameSnapshot characterNameSnapshot,SessionId sessionId,AudioPath audioPath,AudioFormat audioFormat,AudioSize audioSize,AudioDurationMs audioDurationMs,RecognizedText recognizedText,CorrectedText correctedText,Provider provider,Model model,Language language,ProcessingTimeMs processingTimeMs,Status status,ErrorCode errorCode,ErrorMessage errorMessage,CreatedAt createdAt FROM c_AsrTranscriptionRecords WHERE RecordId=#{recordId}")
    Map<String,Object> findByRecordId(String recordId);

    @Select("<script>SELECT Id id,RecordId recordId,RequestId requestId,CharacterId characterId,CharacterNameSnapshot characterNameSnapshot,SessionId sessionId,AudioFormat audioFormat,AudioSize audioSize,AudioDurationMs audioDurationMs,RecognizedText recognizedText,CorrectedText correctedText,Provider provider,Model model,Language language,ProcessingTimeMs processingTimeMs,Status status,ErrorCode errorCode,ErrorMessage errorMessage,CreatedAt createdAt FROM c_AsrTranscriptionRecords <where><if test='characterId != null'>AND CharacterId=#{characterId}</if><if test='status != null'>AND Status=#{status}</if><if test='provider != null'>AND Provider=#{provider}</if><if test='model != null'>AND Model=#{model}</if><if test='keyword != null'>AND (RecognizedText LIKE CONCAT('%',#{keyword},'%') OR CorrectedText LIKE CONCAT('%',#{keyword},'%'))</if><if test='startTime != null'>AND CreatedAt &gt;= #{startTime}</if><if test='endTime != null'>AND CreatedAt &lt;= #{endTime}</if></where> ORDER BY CreatedAt DESC,Id DESC LIMIT #{offset},#{limit}</script>")
    List<Map<String,Object>> findPage(@Param("characterId") String characterId,@Param("status") String status,@Param("provider") String provider,@Param("model") String model,@Param("keyword") String keyword,@Param("startTime") LocalDateTime startTime,@Param("endTime") LocalDateTime endTime,@Param("offset") int offset,@Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM c_AsrTranscriptionRecords <where><if test='characterId != null'>AND CharacterId=#{characterId}</if><if test='status != null'>AND Status=#{status}</if><if test='provider != null'>AND Provider=#{provider}</if><if test='model != null'>AND Model=#{model}</if><if test='keyword != null'>AND (RecognizedText LIKE CONCAT('%',#{keyword},'%') OR CorrectedText LIKE CONCAT('%',#{keyword},'%'))</if><if test='startTime != null'>AND CreatedAt &gt;= #{startTime}</if><if test='endTime != null'>AND CreatedAt &lt;= #{endTime}</if></where></script>")
    long count(@Param("characterId") String characterId,@Param("status") String status,@Param("provider") String provider,@Param("model") String model,@Param("keyword") String keyword,@Param("startTime") LocalDateTime startTime,@Param("endTime") LocalDateTime endTime);

    @Select("SELECT DISTINCT CharacterId characterId,CharacterNameSnapshot characterName FROM c_AsrTranscriptionRecords ORDER BY CharacterNameSnapshot,CharacterId")
    List<Map<String,Object>> findCharacters();
    @Select("SELECT DISTINCT Provider provider,Model model FROM c_AsrTranscriptionRecords ORDER BY Provider,Model")
    List<Map<String,Object>> findProviderModels();

    @Select("SELECT RateLimitRequestsLimit requestLimit,RateLimitRequestsRemaining requestsRemaining,RateLimitRequestsResetAfter requestsResetAfter,RateLimitCapturedAt capturedAt FROM c_AsrTranscriptionRecords WHERE Provider=#{provider} AND Model=#{model} AND RateLimitCapturedAt IS NOT NULL ORDER BY RateLimitCapturedAt DESC,Id DESC LIMIT 1")
    Map<String,Object> findLatestQuotaSnapshot(@Param("provider") String provider,@Param("model") String model);

    @Select("SELECT COALESCE(SUM(AudioDurationMs),0) FROM c_AsrTranscriptionRecords WHERE Provider=#{provider} AND Model=#{model} AND Status='SUCCESS' AND CreatedAt >= #{start} AND CreatedAt < #{end}")
    long sumAudioDurationMs(@Param("provider") String provider,@Param("model") String model,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);

    class Record {
        private Long id;private String requestId;private String characterId;private String characterNameSnapshot;private String sessionId;private String audioPath;private String audioFormat;private long audioSize;private String provider;private String model;private String language;
        public Long getId(){return id;}public void setId(Long v){id=v;}public String getRequestId(){return requestId;}public void setRequestId(String v){requestId=v;}public String getCharacterId(){return characterId;}public void setCharacterId(String v){characterId=v;}public String getCharacterNameSnapshot(){return characterNameSnapshot;}public void setCharacterNameSnapshot(String v){characterNameSnapshot=v;}public String getSessionId(){return sessionId;}public void setSessionId(String v){sessionId=v;}public String getAudioPath(){return audioPath;}public void setAudioPath(String v){audioPath=v;}public String getAudioFormat(){return audioFormat;}public void setAudioFormat(String v){audioFormat=v;}public long getAudioSize(){return audioSize;}public void setAudioSize(long v){audioSize=v;}public String getProvider(){return provider;}public void setProvider(String v){provider=v;}public String getModel(){return model;}public void setModel(String v){model=v;}public String getLanguage(){return language;}public void setLanguage(String v){language=v;}
    }
}
