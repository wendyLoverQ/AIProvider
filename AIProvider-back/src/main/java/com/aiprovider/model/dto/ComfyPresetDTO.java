package com.aiprovider.model.dto;

import java.util.List;
import java.util.Map;

public class ComfyPresetDTO {
    private String name;
    private Map<String, List<String>> selectedOptions;
    private String positiveExtra;
    private String negativeExtra;
    private String positivePrompt;
    private String negativePrompt;
    private String remark;
    private Boolean isDefault;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, List<String>> getSelectedOptions() { return selectedOptions; }
    public void setSelectedOptions(Map<String, List<String>> selectedOptions) { this.selectedOptions = selectedOptions; }
    public String getPositiveExtra() { return positiveExtra; }
    public void setPositiveExtra(String positiveExtra) { this.positiveExtra = positiveExtra; }
    public String getNegativeExtra() { return negativeExtra; }
    public void setNegativeExtra(String negativeExtra) { this.negativeExtra = negativeExtra; }
    public String getPositivePrompt() { return positivePrompt; }
    public void setPositivePrompt(String positivePrompt) { this.positivePrompt = positivePrompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
}
