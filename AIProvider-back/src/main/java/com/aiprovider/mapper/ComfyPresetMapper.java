package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ComfyPresetMapper {
    @Select("SELECT Id id, Title title, ParametersJson parametersJson, OutputFolder outputFolder, Notes notes, IsDefault isDefault FROM ComfyParameterSchemes ORDER BY IsDefault DESC, UpdatedAt DESC")
    List<Map<String, Object>> findAll();

    @Insert("INSERT INTO ComfyParameterSchemes(Title, ParametersJson, OutputFolder, Notes) VALUES(#{title}, CAST(#{parametersJson} AS JSON), #{outputFolder}, #{notes})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PresetInsert preset);

    @Update("UPDATE ComfyParameterSchemes SET Title=#{title}, ParametersJson=CAST(#{parametersJson} AS JSON), OutputFolder=#{outputFolder}, Notes=#{notes} WHERE Id=#{id}")
    int update(PresetInsert preset);
    @Update("UPDATE ComfyParameterSchemes SET IsDefault=FALSE WHERE IsDefault=TRUE")
    void clearDefault();
    @Update("UPDATE ComfyParameterSchemes SET IsDefault=TRUE WHERE Id=#{id}")
    int setDefault(@Param("id") long id);

    @Delete("DELETE FROM ComfyParameterSchemes WHERE Id=#{id}")
    int delete(@Param("id") long id);

    class PresetInsert {
        private Long id; private String title; private String parametersJson; private String outputFolder; private String notes;
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
        public String getParametersJson() { return parametersJson; } public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
        public String getOutputFolder() { return outputFolder; } public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }
        public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    }
}
