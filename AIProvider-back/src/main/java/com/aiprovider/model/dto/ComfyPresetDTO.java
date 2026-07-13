package com.aiprovider.model.dto;

import java.util.Map;

public class ComfyPresetDTO {
    private String title;
    private String outputFolder;
    private String notes;
    private Map<String, Object> parameters;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String outputFolder) { this.outputFolder = outputFolder; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
