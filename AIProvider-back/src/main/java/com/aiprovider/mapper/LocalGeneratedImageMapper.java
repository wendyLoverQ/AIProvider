package com.aiprovider.mapper;

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
    @Insert({"<script>",
            "INSERT INTO c_LocalGeneratedImages(Platform,PathHash,PromptId,ImagePath,FileName,WorkflowId,WorkflowName,Prompt,NegativePrompt,MainModel,LorasJson,Seed,Steps,Cfg,Sampler,Scheduler,Width,Height,TaskCreatedAt,GenerationCompletedAt,GenerationDurationMs) VALUES",
            "<foreach collection='rows' item='row' separator=','>(#{platform},#{row.pathHash},#{row.item.promptId},#{row.item.imagePath},#{row.item.fileName},#{row.item.workflowId},#{row.item.workflowName},#{row.item.prompt},#{row.item.negativePrompt},#{row.item.mainModel},#{row.item.lorasJson},#{row.item.seed},#{row.item.steps},#{row.item.cfg},#{row.item.sampler},#{row.item.scheduler},#{row.item.width},#{row.item.height},#{row.item.taskCreatedAt},#{row.item.generationCompletedAt},#{row.item.generationDurationMs})</foreach>",
            "ON DUPLICATE KEY UPDATE PromptId=VALUES(PromptId),FileName=VALUES(FileName),Status='ACTIVE',WorkflowId=VALUES(WorkflowId),WorkflowName=VALUES(WorkflowName),Prompt=VALUES(Prompt),NegativePrompt=VALUES(NegativePrompt),MainModel=VALUES(MainModel),LorasJson=VALUES(LorasJson),Seed=VALUES(Seed),Steps=VALUES(Steps),Cfg=VALUES(Cfg),Sampler=VALUES(Sampler),Scheduler=VALUES(Scheduler),Width=VALUES(Width),Height=VALUES(Height),TaskCreatedAt=VALUES(TaskCreatedAt),GenerationCompletedAt=VALUES(GenerationCompletedAt),GenerationDurationMs=VALUES(GenerationDurationMs),UpdatedAt=CURRENT_TIMESTAMP(3)",
            "</script>"})
    int upsertBatch(@Param("platform") String platform, @Param("rows") List<Map<String,Object>> rows);

    @Select("SELECT Id id,Platform platform,PathHash pathHash,PromptId promptId,ImagePath imagePath,FileName fileName,Status status,WorkflowId workflowId,WorkflowName workflowName,Prompt prompt,NegativePrompt negativePrompt,MainModel mainModel,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,Width width,Height height,TaskCreatedAt taskCreatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt,UpdatedAt updatedAt " +
            "FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status=#{status} ORDER BY UpdatedAt DESC,Id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> findPage(@Param("platform") String platform, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status=#{status}")
    long count(@Param("platform") String platform, @Param("status") String status);

    @Select({"<script>",
            "SELECT Id id,Platform platform,PromptId promptId,ImagePath imagePath,FileName fileName,Status status,WorkflowId workflowId,WorkflowName workflowName,Prompt prompt,NegativePrompt negativePrompt,MainModel mainModel,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,Width width,Height height,TaskCreatedAt taskCreatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt,UpdatedAt updatedAt FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND PathHash IN",
            "<foreach collection='pathHashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach>",
            "ORDER BY UpdatedAt DESC,Id DESC",
            "</script>"})
    List<Map<String,Object>> findByPathHashes(@Param("platform") String platform, @Param("pathHashes") List<String> pathHashes);

    @Update("<script>UPDATE c_LocalGeneratedImages SET Status='TRASHED',UpdatedAt=CURRENT_TIMESTAMP(3) WHERE Platform=#{platform} AND Status='ACTIVE' AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int trash(@Param("platform") String platform, @Param("ids") List<Long> ids);

    @Update("<script>UPDATE c_LocalGeneratedImages SET Status='ACTIVE',UpdatedAt=CURRENT_TIMESTAMP(3) WHERE Platform=#{platform} AND Status='TRASHED' AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int restore(@Param("platform") String platform, @Param("ids") List<Long> ids);

    @Delete("<script>DELETE FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int delete(@Param("platform") String platform, @Param("ids") List<Long> ids);
}
