package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface PromptTranslationCacheMapper {
    @Select("SELECT Id id, TranslatedText translatedText FROM c_PromptTranslationCache " +
            "WHERE SourceSha256=#{sourceSha256} AND SourceLength=#{sourceLength} " +
            "AND TargetLanguage=#{targetLanguage} AND Provider=#{provider}")
    Map<String, Object> find(@Param("sourceSha256") String sourceSha256,
                             @Param("sourceLength") int sourceLength,
                             @Param("targetLanguage") String targetLanguage,
                             @Param("provider") String provider);

    @Update("UPDATE c_PromptTranslationCache SET HitCount=HitCount+1, LastHitAt=CURRENT_TIMESTAMP(6) WHERE Id=#{id}")
    int recordHit(@Param("id") long id);

    @Insert("INSERT INTO c_PromptTranslationCache(SourceSha256,SourceLength,TargetLanguage,Provider,TranslatedText) " +
            "VALUES(#{sourceSha256},#{sourceLength},#{targetLanguage},#{provider},#{translatedText}) " +
            "ON DUPLICATE KEY UPDATE TranslatedText=VALUES(TranslatedText), UpdatedAt=CURRENT_TIMESTAMP(6)")
    int save(@Param("sourceSha256") String sourceSha256,
             @Param("sourceLength") int sourceLength,
             @Param("targetLanguage") String targetLanguage,
             @Param("provider") String provider,
             @Param("translatedText") String translatedText);
}
