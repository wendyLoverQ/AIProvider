package com.aiprovider.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ComfyPresetMapper {
    @Select("SELECT Id id, Name name, SelectedOptionsJson selectedOptionsJson, PositiveExtra positiveExtra, NegativeExtra negativeExtra, PositivePrompt positivePrompt, NegativePrompt negativePrompt, Remark remark, IsDefault isDefault FROM c_ComfyParameterSchemes ORDER BY IsDefault DESC, UpdatedAt DESC")
    List<Map<String, Object>> findAll();

    @Insert("INSERT INTO c_ComfyParameterSchemes(Name, SelectedOptionsJson, PositiveExtra, NegativeExtra, PositivePrompt, NegativePrompt, Remark, IsDefault) VALUES(#{name}, CAST(#{selectedOptionsJson} AS JSON), #{positiveExtra}, #{negativeExtra}, #{positivePrompt}, #{negativePrompt}, #{remark}, #{isDefault})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PresetRecord preset);

    @Update("UPDATE c_ComfyParameterSchemes SET Name=#{name}, SelectedOptionsJson=CAST(#{selectedOptionsJson} AS JSON), PositiveExtra=#{positiveExtra}, NegativeExtra=#{negativeExtra}, PositivePrompt=#{positivePrompt}, NegativePrompt=#{negativePrompt}, Remark=#{remark}, IsDefault=#{isDefault} WHERE Id=#{id}")
    int update(PresetRecord preset);
    @Update("UPDATE c_ComfyParameterSchemes SET IsDefault=FALSE WHERE IsDefault=TRUE")
    void clearDefault();
    @Update("UPDATE c_ComfyParameterSchemes SET IsDefault=TRUE WHERE Id=#{id}")
    int setDefault(@Param("id") long id);
    @Delete("DELETE FROM c_ComfyParameterSchemes WHERE Id=#{id}")
    int delete(@Param("id") long id);

    class PresetRecord {
        private Long id; private String name; private String selectedOptionsJson;
        private String positiveExtra; private String negativeExtra; private String positivePrompt;
        private String negativePrompt; private String remark; private boolean isDefault;
        public Long getId() { return id; } public void setId(Long id) { this.id = id; }
        public String getName() { return name; } public void setName(String name) { this.name = name; }
        public String getSelectedOptionsJson() { return selectedOptionsJson; } public void setSelectedOptionsJson(String value) { this.selectedOptionsJson = value; }
        public String getPositiveExtra() { return positiveExtra; } public void setPositiveExtra(String value) { this.positiveExtra = value; }
        public String getNegativeExtra() { return negativeExtra; } public void setNegativeExtra(String value) { this.negativeExtra = value; }
        public String getPositivePrompt() { return positivePrompt; } public void setPositivePrompt(String value) { this.positivePrompt = value; }
        public String getNegativePrompt() { return negativePrompt; } public void setNegativePrompt(String value) { this.negativePrompt = value; }
        public String getRemark() { return remark; } public void setRemark(String value) { this.remark = value; }
        public boolean isDefault() { return isDefault; } public void setDefault(boolean value) { this.isDefault = value; }
    }
}
