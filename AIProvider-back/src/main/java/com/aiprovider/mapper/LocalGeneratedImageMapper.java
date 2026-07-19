package com.aiprovider.mapper;

import com.aiprovider.model.dto.LocalGeneratedImageItemDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;

@Mapper
public interface LocalGeneratedImageMapper {
    @Insert("INSERT INTO c_LocalGeneratedImages(Platform,PathHash,PromptId,ImagePath,FileName,WorkflowId,WorkflowName,Prompt,NegativePrompt,LorasJson,Seed,Steps,Cfg,Sampler,Scheduler,Width,Height,TaskCreatedAt,GenerationCompletedAt,GenerationDurationMs) " +
            "VALUES(#{platform},#{pathHash},#{item.promptId},#{item.imagePath},#{item.fileName},#{item.workflowId},#{item.workflowName},#{item.prompt},#{item.negativePrompt},#{item.lorasJson},#{item.seed},#{item.steps},#{item.cfg},#{item.sampler},#{item.scheduler},#{item.width},#{item.height},#{item.taskCreatedAt},#{item.generationCompletedAt},#{item.generationDurationMs}) " +
            "ON DUPLICATE KEY UPDATE PromptId=VALUES(PromptId),FileName=VALUES(FileName),Status='ACTIVE',WorkflowId=VALUES(WorkflowId),WorkflowName=VALUES(WorkflowName),Prompt=VALUES(Prompt),NegativePrompt=VALUES(NegativePrompt),LorasJson=VALUES(LorasJson),Seed=VALUES(Seed),Steps=VALUES(Steps),Cfg=VALUES(Cfg),Sampler=VALUES(Sampler),Scheduler=VALUES(Scheduler),Width=VALUES(Width),Height=VALUES(Height),TaskCreatedAt=VALUES(TaskCreatedAt),GenerationCompletedAt=VALUES(GenerationCompletedAt),GenerationDurationMs=VALUES(GenerationDurationMs),UpdatedAt=CURRENT_TIMESTAMP(3)")
    int upsert(@Param("platform") String platform, @Param("pathHash") String pathHash, @Param("item") LocalGeneratedImageItemDTO item);

    @Select("SELECT Id id,Platform platform,PathHash pathHash,PromptId promptId,ImagePath imagePath,FileName fileName,Status status,WorkflowId workflowId,WorkflowName workflowName,Prompt prompt,NegativePrompt negativePrompt,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,Width width,Height height,TaskCreatedAt taskCreatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt,UpdatedAt updatedAt " +
            "FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status=#{status} ORDER BY UpdatedAt DESC,Id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> findPage(@Param("platform") String platform, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status=#{status}")
    long count(@Param("platform") String platform, @Param("status") String status);

    @Update("<script>UPDATE c_LocalGeneratedImages SET Status='TRASHED',UpdatedAt=CURRENT_TIMESTAMP(3) WHERE Platform=#{platform} AND Status='ACTIVE' AND PathHash IN " +
            "<foreach collection='pathHashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach></script>")
    int trash(@Param("platform") String platform, @Param("pathHashes") List<String> pathHashes);

    @Update("<script>UPDATE c_LocalGeneratedImages SET Status='ACTIVE',UpdatedAt=CURRENT_TIMESTAMP(3) WHERE Platform=#{platform} AND Status='TRASHED' AND PathHash IN " +
            "<foreach collection='pathHashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach></script>")
    int restore(@Param("platform") String platform, @Param("pathHashes") List<String> pathHashes);

    @Delete("<script>DELETE FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND PathHash IN " +
            "<foreach collection='pathHashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach></script>")
    int delete(@Param("platform") String platform, @Param("pathHashes") List<String> pathHashes);
}
