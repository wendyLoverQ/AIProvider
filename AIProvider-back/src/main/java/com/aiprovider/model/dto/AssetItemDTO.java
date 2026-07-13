package com.aiprovider.model.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class AssetItemDTO {
    private String localPath;
    private String localUrl;
    private String fileName;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String prompt;
    private String negativePrompt;
    private String lorasJson;
    private Long seed;
    private Integer steps;
    private Double cfg;
    private String sampler;
    private String scheduler;
    private String workflowId;
    private LocalDateTime generatedAt;

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public String getLocalUrl() { return localUrl; }
    public void setLocalUrl(String localUrl) { this.localUrl = localUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
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
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    @JsonSetter("generatedAt")
    public void setGeneratedAt(String generatedAt) {
        if (generatedAt == null || generatedAt.trim().isEmpty()) { this.generatedAt = null; return; }
        try { this.generatedAt = OffsetDateTime.parse(generatedAt).toLocalDateTime(); }
        catch (DateTimeParseException ignored) { this.generatedAt = LocalDateTime.parse(generatedAt); }
    }
}
