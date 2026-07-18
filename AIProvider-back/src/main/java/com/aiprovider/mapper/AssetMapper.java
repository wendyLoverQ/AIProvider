package com.aiprovider.mapper;

import com.aiprovider.model.dto.AssetItemDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssetMapper {
    @Insert("INSERT INTO c_GeneratedAssets(Platform,PathHash,LocalPath,LocalUrl,FileName,FileSize,Width,Height,AssetType,Status,MimeType,Prompt,NegativePrompt,LorasJson,Seed,Steps,Cfg,Sampler,Scheduler,WorkflowId,GeneratedAt,GenerationCompletedAt,GenerationDurationMs) " +
            "VALUES(#{platform},#{pathHash},#{item.localPath},#{item.localUrl},#{item.fileName},#{item.fileSize},#{item.width},#{item.height},#{item.assetType},#{item.status},#{item.mimeType},#{item.prompt},#{item.negativePrompt},#{item.lorasJson},#{item.seed},#{item.steps},#{item.cfg},#{item.sampler},#{item.scheduler},#{item.workflowId},#{item.generatedAt},#{item.generationCompletedAt},#{item.generationDurationMs}) " +
            "ON DUPLICATE KEY UPDATE LocalPath=VALUES(LocalPath),LocalUrl=VALUES(LocalUrl),FileName=VALUES(FileName),FileSize=VALUES(FileSize),Width=VALUES(Width),Height=VALUES(Height),AssetType=VALUES(AssetType),Status=VALUES(Status),MimeType=VALUES(MimeType),Prompt=VALUES(Prompt),NegativePrompt=VALUES(NegativePrompt),LorasJson=VALUES(LorasJson),Seed=VALUES(Seed),Steps=VALUES(Steps),Cfg=VALUES(Cfg),Sampler=VALUES(Sampler),Scheduler=VALUES(Scheduler),WorkflowId=VALUES(WorkflowId),GeneratedAt=VALUES(GeneratedAt),GenerationCompletedAt=VALUES(GenerationCompletedAt),GenerationDurationMs=VALUES(GenerationDurationMs),UpdatedAt=CURRENT_TIMESTAMP(3)")
    int upsert(@Param("platform") String platform, @Param("pathHash") String pathHash, @Param("item") AssetItemDTO item);

    @Select("<script>SELECT Id id,Platform platform,LocalPath localPath,LocalUrl localUrl,FileName fileName,FileSize fileSize,Width width,Height height,AssetType assetType,Status status,MimeType mimeType,Prompt prompt,NegativePrompt negativePrompt,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,WorkflowId workflowId,GeneratedAt generatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt " +
            "FROM c_GeneratedAssets WHERE Platform=#{platform} " +
            "<if test='status != null and status != \"\"'>AND Status=#{status} </if>" +
            "ORDER BY COALESCE(GeneratedAt,CreatedAt) DESC,Id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> findPage(@Param("platform") String platform, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT Id id,Platform platform,LocalPath localPath,LocalUrl localUrl,FileName fileName,FileSize fileSize,Width width,Height height,AssetType assetType,Status status,MimeType mimeType,Prompt prompt,NegativePrompt negativePrompt,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,WorkflowId workflowId,GeneratedAt generatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt " +
            "FROM c_GeneratedAssets WHERE Platform=#{platform} AND PathHash IN " +
            "<foreach collection='pathHashes' item='pathHash' open='(' separator=',' close=')'>#{pathHash}</foreach> " +
            "ORDER BY COALESCE(GenerationCompletedAt,GeneratedAt,CreatedAt) DESC,Id DESC</script>")
    List<Map<String,Object>> findByPathHashes(@Param("platform") String platform, @Param("pathHashes") List<String> pathHashes);

    @Select("<script>SELECT COUNT(*) FROM c_GeneratedAssets WHERE Platform=#{platform} " +
            "<if test='status != null and status != \"\"'>AND Status=#{status}</if></script>")
    long count(@Param("platform") String platform, @Param("status") String status);

    @Select("SELECT Prompt prompt,NegativePrompt negativePrompt,COUNT(*) weight FROM c_GeneratedAssets " +
            "WHERE Platform=#{platform} AND AssetType='image' AND (NULLIF(TRIM(Prompt),'') IS NOT NULL OR NULLIF(TRIM(NegativePrompt),'') IS NOT NULL) " +
            "GROUP BY Prompt,NegativePrompt ORDER BY weight DESC LIMIT 500")
    List<Map<String,Object>> findImagePromptPool(@Param("platform") String platform);

    @Select("SELECT Id id,Platform platform,LocalPath localPath,FileName fileName,Status status FROM c_GeneratedAssets WHERE Id=#{id}")
    Map<String,Object> findById(@Param("id") long id);

    @Delete("<script>DELETE FROM c_GeneratedAssets WHERE Platform=#{platform} AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByIds(@Param("platform") String platform, @Param("ids") List<Long> ids);

    @Update("<script>UPDATE c_GeneratedAssets SET Status=#{status},UpdatedAt=CURRENT_TIMESTAMP(3) WHERE Platform=#{platform} AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int updateStatus(@Param("platform") String platform, @Param("ids") List<Long> ids, @Param("status") String status);
}