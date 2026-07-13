package com.aiprovider.mapper;

import com.aiprovider.model.dto.AssetItemDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssetMapper {
    @Insert("INSERT INTO c_GeneratedAssets(Platform,PathHash,LocalPath,LocalUrl,FileName,FileSize,Width,Height,Prompt,NegativePrompt,LorasJson,Seed,Steps,Cfg,Sampler,Scheduler,WorkflowId,GeneratedAt) " +
            "VALUES(#{platform},#{pathHash},#{item.localPath},#{item.localUrl},#{item.fileName},#{item.fileSize},#{item.width},#{item.height},#{item.prompt},#{item.negativePrompt},#{item.lorasJson},#{item.seed},#{item.steps},#{item.cfg},#{item.sampler},#{item.scheduler},#{item.workflowId},#{item.generatedAt}) " +
            "ON DUPLICATE KEY UPDATE LocalPath=VALUES(LocalPath),LocalUrl=VALUES(LocalUrl),FileName=VALUES(FileName),FileSize=VALUES(FileSize),Width=VALUES(Width),Height=VALUES(Height),Prompt=VALUES(Prompt),NegativePrompt=VALUES(NegativePrompt),LorasJson=VALUES(LorasJson),Seed=VALUES(Seed),Steps=VALUES(Steps),Cfg=VALUES(Cfg),Sampler=VALUES(Sampler),Scheduler=VALUES(Scheduler),WorkflowId=VALUES(WorkflowId),GeneratedAt=VALUES(GeneratedAt),UpdatedAt=CURRENT_TIMESTAMP(3)")
    int upsert(@Param("platform") String platform, @Param("pathHash") String pathHash, @Param("item") AssetItemDTO item);

    @Select("SELECT Id id,Platform platform,LocalPath localPath,LocalUrl localUrl,FileName fileName,FileSize fileSize,Width width,Height height,Prompt prompt,NegativePrompt negativePrompt,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,WorkflowId workflowId,GeneratedAt generatedAt,CreatedAt createdAt " +
            "FROM c_GeneratedAssets WHERE Platform=#{platform} ORDER BY COALESCE(GeneratedAt,CreatedAt) DESC,Id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> findPage(@Param("platform") String platform, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM c_GeneratedAssets WHERE Platform=#{platform}")
    long count(@Param("platform") String platform);

    @Select("SELECT Id id,Platform platform,LocalPath localPath,FileName fileName FROM c_GeneratedAssets WHERE Id=#{id}")
    Map<String,Object> findById(@Param("id") long id);

    @Delete("<script>DELETE FROM c_GeneratedAssets WHERE Platform=#{platform} AND Id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByIds(@Param("platform") String platform, @Param("ids") List<Long> ids);
}
