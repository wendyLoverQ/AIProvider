package com.aiprovider.mapper;

import com.aiprovider.model.dto.ComfyTaskRecordDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ComfyTaskMapper {
    @Insert("INSERT INTO c_ComfyUiTasks(Id,PromptId,WorkflowName,WorkflowId,PromptSchemeName,PositivePrompt,NegativePrompt,MainModel,ParametersJson,InputFile,InputSha256,InputFileName,Status,Progress) VALUES(#{promptId},#{promptId},#{workflowName},#{workflowId},#{promptSchemeName},#{positivePrompt},#{negativePrompt},#{mainModel},CAST(#{parametersJson} AS JSON),#{inputFile},#{inputSha256},#{inputFileName},#{status},0) ON DUPLICATE KEY UPDATE MainModel=VALUES(MainModel),Status=VALUES(Status),UpdatedAt=CURRENT_TIMESTAMP(6)")
    int save(ComfyTaskRecordDTO dto);
    @Insert({"<script>",
        "INSERT INTO c_ComfyUiTasks(Id,PromptId,WorkflowName,WorkflowId,PromptSchemeName,PositivePrompt,NegativePrompt,MainModel,ParametersJson,InputFile,InputSha256,InputFileName,Status,Progress) VALUES",
        "<foreach collection='items' item='item' separator=','>",
        "(#{item.promptId},#{item.promptId},#{item.workflowName},#{item.workflowId},#{item.promptSchemeName},#{item.positivePrompt},#{item.negativePrompt},#{item.mainModel},CAST(#{item.parametersJson} AS JSON),#{item.inputFile},#{item.inputSha256},#{item.inputFileName},#{item.status},0)",
        "</foreach>",
        "ON DUPLICATE KEY UPDATE MainModel=VALUES(MainModel),Status=VALUES(Status),UpdatedAt=CURRENT_TIMESTAMP(6)",
        "</script>"})
    int saveBatch(@Param("items") List<ComfyTaskRecordDTO> items);
    @Select("SELECT Id id,PromptId promptId,WorkflowId workflowId,WorkflowName workflowName,PromptSchemeName promptSchemeName,MainModel mainModel,InputFile inputFile,InputFileName inputFileName,Status status,OutputFile outputFile,ResultPathsJson resultPathsJson,CreatedAt createdAt,CompletedAt completedAt FROM c_ComfyUiTasks WHERE WorkflowId=#{workflowId} AND InputSha256=#{inputSha256} AND Status IN ('QUEUED','RUNNING','SUCCEEDED') ORDER BY CreatedAt DESC LIMIT 1")
    Map<String,Object> findDuplicate(@Param("workflowId") String workflowId,@Param("inputSha256") String inputSha256);
    @Select({"<script>",
        "SELECT DISTINCT InputSha256 FROM c_ComfyUiTasks WHERE WorkflowId=#{workflowId} AND Status IN ('QUEUED','RUNNING','SUCCEEDED') AND InputSha256 IN",
        "<foreach collection='hashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach>",
        "</script>"})
    List<String> findDuplicateHashes(@Param("workflowId") String workflowId, @Param("hashes") List<String> hashes);
    @Update("UPDATE c_ComfyUiTasks SET Status='SUCCEEDED',Progress=100,OutputFile=#{outputFile},ResultPathsJson=CAST(#{resultPathsJson} AS JSON),CompletedAt=CURRENT_TIMESTAMP(3),UpdatedAt=CURRENT_TIMESTAMP(6) WHERE PromptId=#{promptId}")
    int complete(@Param("promptId") String promptId,@Param("outputFile") String outputFile,@Param("resultPathsJson") String resultPathsJson);
}
