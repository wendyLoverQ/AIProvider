package com.aiprovider.model.vo;

import java.util.List;
import java.util.Map;

public class ComfyPresetVO {
    private final Long id;
    private final String name;
    private final Map<String, List<String>> selectedOptions;
    private final String positiveExtra;
    private final String negativeExtra;
    private final String positivePrompt;
    private final String negativePrompt;
    private final String remark;
    private final boolean isDefault;

    public ComfyPresetVO(Long id, String name, Map<String, List<String>> selectedOptions,
                         String positiveExtra, String negativeExtra, String positivePrompt,
                         String negativePrompt, String remark, boolean isDefault) {
        this.id = id; this.name = name; this.selectedOptions = selectedOptions;
        this.positiveExtra = positiveExtra; this.negativeExtra = negativeExtra;
        this.positivePrompt = positivePrompt; this.negativePrompt = negativePrompt;
        this.remark = remark; this.isDefault = isDefault;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Map<String, List<String>> getSelectedOptions() { return selectedOptions; }
    public String getPositiveExtra() { return positiveExtra; }
    public String getNegativeExtra() { return negativeExtra; }
    public String getPositivePrompt() { return positivePrompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public String getRemark() { return remark; }
    public boolean getIsDefault() { return isDefault; }
}
