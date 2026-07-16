package com.aiprovider.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface PromptCatalogMapper {
    @Select("SELECT p.Id id, p.Category category, p.Name name, p.Prompt prompt, p.Type type, p.ReverseId reverseId, p.Prompt positivePrompt, n.Prompt negativePrompt, p.SortOrder sortOrder, p.Enabled enabled, p.AllowMultiple allowMultiple FROM c_PromptOptions p LEFT JOIN c_PromptOptions n ON n.Id=p.ReverseId AND n.Type='negative' WHERE p.Enabled=TRUE AND p.Type='positive' ORDER BY p.Category, p.SortOrder, p.Id")
    List<Map<String, Object>> findEnabledOptions();

    @Select("SELECT Id id, Category category, Name name, Prompt prompt, Type type, ReverseId reverseId, NULL positivePrompt, Prompt negativePrompt, SortOrder sortOrder, Enabled enabled, AllowMultiple allowMultiple FROM c_PromptOptions WHERE Enabled=TRUE AND Type='negative' ORDER BY Category, SortOrder, Id")
    List<Map<String, Object>> findEnabledNegativeOptions();

    @Select({"<script>",
            "SELECT Id id, Category category, Name name, Prompt prompt, Type type, ReverseId reverseId, SortOrder sortOrder, Enabled enabled, AllowMultiple allowMultiple FROM c_PromptOptions",
            "<where>",
            "<if test='query != null'>AND (Id LIKE CONCAT('%',#{query},'%') OR Name LIKE CONCAT('%',#{query},'%') OR Prompt LIKE CONCAT('%',#{query},'%'))</if>",
            "<if test='category != null'>AND Category=#{category}</if>",
            "<if test='enabled != null'>AND Enabled=#{enabled}</if>",
            "</where>",
            "ORDER BY Category, Type, SortOrder, Id LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<Map<String, Object>> findOptionPage(@Param("query") String query, @Param("category") String category, @Param("enabled") Boolean enabled, @Param("limit") int limit, @Param("offset") long offset);

    @Select({"<script>",
            "SELECT COUNT(*) FROM c_PromptOptions",
            "<where>",
            "<if test='query != null'>AND (Id LIKE CONCAT('%',#{query},'%') OR Name LIKE CONCAT('%',#{query},'%') OR Prompt LIKE CONCAT('%',#{query},'%'))</if>",
            "<if test='category != null'>AND Category=#{category}</if>",
            "<if test='enabled != null'>AND Enabled=#{enabled}</if>",
            "</where>",
            "</script>"})
    long countOptions(@Param("query") String query, @Param("category") String category, @Param("enabled") Boolean enabled);

    @Select("SELECT Prompt FROM c_PromptTemplates WHERE Id='general_negative' AND Enabled=TRUE")
    String findGeneralNegativePrompt();

    @Select("SELECT COUNT(*) FROM c_PromptOptions WHERE Id=#{id}")
    int countOption(String id);

    @Insert("INSERT INTO c_PromptOptions(Id,Category,Name,Prompt,Type,ReverseId,SortOrder,Enabled,AllowMultiple) VALUES(#{id},#{category},#{name},#{prompt},#{type},#{reverseId},#{sortOrder},#{enabled},#{allowMultiple})")
    int insertOption(OptionRecord option);

    @Update("UPDATE c_PromptOptions SET Category=#{category},Name=#{name},Prompt=#{prompt},Type=#{type},ReverseId=#{reverseId},SortOrder=#{sortOrder},Enabled=#{enabled},AllowMultiple=#{allowMultiple} WHERE Id=#{id}")
    int updateOption(OptionRecord option);

    @Select("SELECT COUNT(*) FROM c_ComfyParameterSchemes WHERE JSON_SEARCH(SelectedOptionsJson,'one',#{id}) IS NOT NULL")
    int countSchemeReferences(String id);

    @Delete("DELETE FROM c_PromptOptions WHERE Id=#{id}")
    int deleteOption(String id);

    class OptionRecord {
        private String id; private String category; private String name; private String prompt; private String type; private String reverseId;
        private int sortOrder; private boolean enabled; private boolean allowMultiple;
        public String getId() { return id; } public void setId(String id) { this.id = id; }
        public String getCategory() { return category; } public void setCategory(String category) { this.category = category; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public String getPrompt() { return prompt; } public void setPrompt(String value) { this.prompt = value; }
        public String getType() { return type; } public void setType(String value) { this.type = value; }
        public String getReverseId() { return reverseId; } public void setReverseId(String value) { this.reverseId = value; }
        public int getSortOrder() { return sortOrder; } public void setSortOrder(int value) { this.sortOrder = value; }
        public boolean isEnabled() { return enabled; } public void setEnabled(boolean value) { this.enabled = value; }
        public boolean isAllowMultiple() { return allowMultiple; } public void setAllowMultiple(boolean value) { this.allowMultiple = value; }
    }
}
