package com.aiprovider.model.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class LocalGeneratedImageItemDTO {
    private String promptId;
    private String imagePath;
    private String fileName;
    private String workflowId;
    private String workflowName;
    private String prompt;
    private String negativePrompt;
    private String mainModel;
    private String lorasJson;
    private Long seed;
    private Integer steps;
    private Double cfg;
    private String sampler;
    private String scheduler;
    private Integer width;
    private Integer height;
    private LocalDateTime taskCreatedAt;
    private LocalDateTime generationCompletedAt;
    private Long generationDurationMs;

    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getWorkflowName() { return workflowName; }
    public void setWorkflowName(String workflowName) { this.workflowName = workflowName; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
    public String getMainModel() { return mainModel; }
    public void setMainModel(String mainModel) { this.mainModel = mainModel; }
    public String getLorasJson() { return lorasJson; }
    public void setLorasJson(String lorasJson) { this.lorasJson = lorasJson; }
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }
    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }
    public Double getCfg() { return cfg; }
    public void setCfg(Double cfg) { this.cfg = cfg; }
    public String getSampler() { return sampler; }
    public void setSampler(String sampler) { this.sampler = sampler; }
    public String getScheduler() { return scheduler; }
    public void setScheduler(String scheduler) { this.scheduler = scheduler; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public LocalDateTime getTaskCreatedAt() { return taskCreatedAt; }
    @JsonSetter("taskCreatedAt") public void setTaskCreatedAt(String value) { taskCreatedAt = parseTime(value); }
    public LocalDateTime getGenerationCompletedAt() { return generationCompletedAt; }
    @JsonSetter("generationCompletedAt") public void setGenerationCompletedAt(String value) { generationCompletedAt = parseTime(value); }
    public Long getGenerationDurationMs() { return generationDurationMs; }
    public void setGenerationDurationMs(Long generationDurationMs) { this.generationDurationMs = generationDurationMs; }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (DateTimeParseException ignored) { return LocalDateTime.parse(value); }
    }
}
