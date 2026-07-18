package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;

@Mapper
public interface ContentAiMapper {
    @Select("SELECT AiGenerationEnabled enabled,GeminiApiBaseUrl apiBaseUrl,GeminiModel model,GeminiApiKeyEncrypted apiKeyEncrypted,GeminiApiKeyHint apiKeyHint,ContentRewritePrompt contentRewritePrompt,CommentReplyPrompt commentReplyPrompt,GenerationTemperature temperature,MaxOutputTokens maxOutputTokens,UpdatedAt updatedAt FROM c_ContentOperationSettings WHERE Id=1")
    Map<String,Object> findConfig();

    @Update("UPDATE c_ContentOperationSettings SET AiGenerationEnabled=#{enabled},GeminiApiBaseUrl=#{apiBaseUrl},GeminiModel=#{model},GeminiApiKeyEncrypted=#{apiKeyEncrypted},GeminiApiKeyHint=#{apiKeyHint},ContentRewritePrompt=#{contentRewritePrompt},CommentReplyPrompt=#{commentReplyPrompt},GenerationTemperature=#{temperature},MaxOutputTokens=#{maxOutputTokens},ContentModel=#{model} WHERE Id=1")
    int updateConfig(ConfigRecord record);

    @Insert("INSERT INTO c_ContentAiGenerations(GenerationType,Provider,ModelName,InputJson,SystemPromptSnapshot,Status) VALUES(#{generationType},'GEMINI',#{modelName},CAST(#{inputJson} AS JSON),#{systemPromptSnapshot},'PROCESSING')")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertGeneration(GenerationRecord record);

    @Update("UPDATE c_ContentAiGenerations SET OutputText=#{outputText},Status='SUCCEEDED',LatencyMs=#{latencyMs},FinishedAt=NOW(3) WHERE Id=#{id} AND Status='PROCESSING'")
    int markSucceeded(@Param("id") long id,@Param("outputText") String outputText,@Param("latencyMs") long latencyMs);

    @Update("UPDATE c_ContentAiGenerations SET Status='FAILED',ErrorCode=#{errorCode},ErrorMessage=#{errorMessage},LatencyMs=#{latencyMs},FinishedAt=NOW(3) WHERE Id=#{id} AND Status='PROCESSING'")
    int markFailed(@Param("id") long id,@Param("errorCode") String errorCode,@Param("errorMessage") String errorMessage,@Param("latencyMs") long latencyMs);

    class ConfigRecord {
        private boolean enabled; private String apiBaseUrl; private String model; private String apiKeyEncrypted; private String apiKeyHint;
        private String contentRewritePrompt; private String commentReplyPrompt; private java.math.BigDecimal temperature; private int maxOutputTokens;
        public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public String getApiBaseUrl(){return apiBaseUrl;} public void setApiBaseUrl(String v){apiBaseUrl=v;}
        public String getModel(){return model;} public void setModel(String v){model=v;} public String getApiKeyEncrypted(){return apiKeyEncrypted;} public void setApiKeyEncrypted(String v){apiKeyEncrypted=v;}
        public String getApiKeyHint(){return apiKeyHint;} public void setApiKeyHint(String v){apiKeyHint=v;} public String getContentRewritePrompt(){return contentRewritePrompt;} public void setContentRewritePrompt(String v){contentRewritePrompt=v;}
        public String getCommentReplyPrompt(){return commentReplyPrompt;} public void setCommentReplyPrompt(String v){commentReplyPrompt=v;} public java.math.BigDecimal getTemperature(){return temperature;} public void setTemperature(java.math.BigDecimal v){temperature=v;}
        public int getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(int v){maxOutputTokens=v;}
    }
    class GenerationRecord {
        private Long id; private String generationType; private String modelName; private String inputJson; private String systemPromptSnapshot;
        public Long getId(){return id;} public void setId(Long v){id=v;} public String getGenerationType(){return generationType;} public void setGenerationType(String v){generationType=v;}
        public String getModelName(){return modelName;} public void setModelName(String v){modelName=v;} public String getInputJson(){return inputJson;} public void setInputJson(String v){inputJson=v;}
        public String getSystemPromptSnapshot(){return systemPromptSnapshot;} public void setSystemPromptSnapshot(String v){systemPromptSnapshot=v;}
    }
}
