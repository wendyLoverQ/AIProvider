package com.aiprovider.model.vo;

import java.util.Map;

public class ComfyPresetVO {
    private Long id;
    private String title;
    private String outputFolder;
    private Map<String, Object> parameters;
    private String notes;
    private boolean defaultPreset;

    public ComfyPresetVO(Long id, String title, String outputFolder, Map<String, Object> parameters) {
        this(id, title, outputFolder, parameters, null, false);
    }
    public ComfyPresetVO(Long id, String title, String outputFolder, Map<String, Object> parameters,
                         String notes, boolean defaultPreset) {
        this.id = id; this.title = title;
        this.outputFolder = outputFolder; this.parameters = parameters; this.notes = notes; this.defaultPreset = defaultPreset;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getOutputFolder() { return outputFolder; }
    public Map<String, Object> getParameters() { return parameters; }
    public String getNotes() { return notes; }
    public boolean isDefaultPreset() { return defaultPreset; }
}
