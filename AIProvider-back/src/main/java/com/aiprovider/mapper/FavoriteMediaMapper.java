package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface FavoriteMediaMapper {
    @Insert("INSERT INTO c_FavoriteMedia(AssetId,StoragePath,ThumbnailPath,OriginalFileName,Title,MediaType,ContentType,FileSize,Sha256,Width,Height,Prompt,SourcePlatform) " +
            "VALUES(#{row.assetId},#{row.storagePath},#{row.thumbnailPath},#{row.originalFileName},#{row.title},#{row.mediaType},#{row.contentType},#{row.fileSize},#{row.sha256},#{row.width},#{row.height},#{row.prompt},#{row.sourcePlatform})")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Row row);

    @Insert({"<script>",
            "INSERT INTO c_FavoriteMedia(AssetId,StoragePath,ThumbnailPath,OriginalFileName,Title,MediaType,ContentType,FileSize,Sha256,Width,Height,Prompt,SourcePlatform) VALUES",
            "<foreach collection='rows' item='row' separator=','>(#{row.assetId},#{row.storagePath},#{row.thumbnailPath},#{row.originalFileName},#{row.title},#{row.mediaType},#{row.contentType},#{row.fileSize},#{row.sha256},#{row.width},#{row.height},#{row.prompt},#{row.sourcePlatform})</foreach>",
            "</script>"})
    int insertBatch(@Param("rows") List<Row> rows);

    @Select("SELECT Id id,AssetId assetId,StoragePath storagePath,ThumbnailPath thumbnailPath,OriginalFileName originalFileName,Title title,MediaType mediaType,ContentType contentType,FileSize fileSize,Sha256 sha256,Width width,Height height,Prompt prompt,SourcePlatform sourcePlatform,CreatedAt createdAt FROM c_FavoriteMedia ORDER BY CreatedAt DESC,Id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String,Object>> findPage(@Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM c_FavoriteMedia") long count();
    @Select("SELECT Id id,AssetId assetId,StoragePath storagePath,ThumbnailPath thumbnailPath,OriginalFileName originalFileName,Title title,MediaType mediaType,ContentType contentType,FileSize fileSize,Sha256 sha256,Width width,Height height,Prompt prompt,SourcePlatform sourcePlatform,CreatedAt createdAt FROM c_FavoriteMedia WHERE Id=#{id}")
    Map<String,Object> findById(@Param("id") long id);
    @Select("SELECT Id id,AssetId assetId,StoragePath storagePath,ThumbnailPath thumbnailPath,OriginalFileName originalFileName,Title title,MediaType mediaType,ContentType contentType,FileSize fileSize,Sha256 sha256,Width width,Height height,Prompt prompt,SourcePlatform sourcePlatform,CreatedAt createdAt FROM c_FavoriteMedia WHERE Sha256=#{sha256}")
    Map<String,Object> findBySha256(@Param("sha256") String sha256);
    @Select("<script>SELECT Sha256 FROM c_FavoriteMedia WHERE Sha256 IN <foreach collection='hashes' item='hash' open='(' separator=',' close=')'>#{hash}</foreach></script>")
    List<String> findExistingSha256s(@Param("hashes") List<String> hashes);
    @Delete("<script>DELETE FROM c_FavoriteMedia WHERE Id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByIds(@Param("ids") List<Long> ids);

    class Row {
        private Long id, assetId, fileSize;
        private String storagePath, thumbnailPath, originalFileName, title, mediaType, contentType, sha256, prompt, sourcePlatform;
        private Integer width, height;
        public Long getId(){return id;} public void setId(Long value){id=value;}
        public Long getAssetId(){return assetId;} public void setAssetId(Long value){assetId=value;}
        public Long getFileSize(){return fileSize;} public void setFileSize(Long value){fileSize=value;}
        public String getStoragePath(){return storagePath;} public void setStoragePath(String value){storagePath=value;}
        public String getThumbnailPath(){return thumbnailPath;} public void setThumbnailPath(String value){thumbnailPath=value;}
        public String getOriginalFileName(){return originalFileName;} public void setOriginalFileName(String value){originalFileName=value;}
        public String getTitle(){return title;} public void setTitle(String value){title=value;}
        public String getMediaType(){return mediaType;} public void setMediaType(String value){mediaType=value;}
        public String getContentType(){return contentType;} public void setContentType(String value){contentType=value;}
        public String getSha256(){return sha256;} public void setSha256(String value){sha256=value;}
        public String getPrompt(){return prompt;} public void setPrompt(String value){prompt=value;}
        public String getSourcePlatform(){return sourcePlatform;} public void setSourcePlatform(String value){sourcePlatform=value;}
        public Integer getWidth(){return width;} public void setWidth(Integer value){width=value;}
        public Integer getHeight(){return height;} public void setHeight(Integer value){height=value;}
    }
}
