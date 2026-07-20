package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface GalleryRecycleBinMapper {
    @Select("SELECT * FROM (" +
            "SELECT 'local' source,Id recordId,NULL assetId,Platform platform,ImagePath localPath,NULL localUrl,FileName fileName,NULL fileSize,Width width,Height height,Status status,'ACTIVE' trashOriginalStatus,Prompt prompt,NegativePrompt negativePrompt,MainModel mainModel,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,WorkflowId workflowId,TaskCreatedAt generatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt,UpdatedAt updatedAt FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status='TRASHED' " +
            "UNION ALL " +
            "SELECT 'asset' source,Id recordId,Id assetId,Platform platform,LocalPath localPath,LocalUrl localUrl,FileName fileName,FileSize fileSize,Width width,Height height,Status status,TrashOriginalStatus trashOriginalStatus,Prompt prompt,NegativePrompt negativePrompt,MainModel mainModel,LorasJson lorasJson,Seed seed,Steps steps,Cfg cfg,Sampler sampler,Scheduler scheduler,WorkflowId workflowId,GeneratedAt generatedAt,GenerationCompletedAt generationCompletedAt,GenerationDurationMs generationDurationMs,CreatedAt createdAt,UpdatedAt updatedAt FROM c_GeneratedAssets WHERE Platform=#{platform} AND Status='TRASHED'" +
            ") queue ORDER BY updatedAt DESC,recordId DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> findPage(@Param("platform") String platform, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT (SELECT COUNT(*) FROM c_LocalGeneratedImages WHERE Platform=#{platform} AND Status='TRASHED') + " +
            "(SELECT COUNT(*) FROM c_GeneratedAssets WHERE Platform=#{platform} AND Status='TRASHED')")
    long count(@Param("platform") String platform);
}
