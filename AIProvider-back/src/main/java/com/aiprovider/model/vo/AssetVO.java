package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class AssetVO {
    private final long id;
    private final String platform;
    private final String localPath;
    private final String localUrl;
    private final String fileName;
    private final long fileSize;
    private final Integer width;
    private final Integer height;
    private final String assetType;
    private final String mimeType;
    private final String status;
    private final String trashOriginalStatus;
    private final String prompt;
    private final String negativePrompt;
    private final String mainModel;
    private final String lorasJson;
    private final Long seed;
    private final Integer steps;
    private final Double cfg;
    private final String sampler;
    private final String scheduler;
    private final String workflowId;
    private final LocalDateTime generatedAt;
    private final LocalDateTime generationCompletedAt;
    private final Long generationDurationMs;
    private final LocalDateTime createdAt;

    public AssetVO(long id, String platform, String localPath, String localUrl, String fileName, long fileSize,
                   Integer width, Integer height, String assetType, String mimeType, String status, String trashOriginalStatus, String prompt, String negativePrompt, String mainModel, String lorasJson, Long seed,
                   Integer steps, Double cfg, String sampler, String scheduler, String workflowId,
                   LocalDateTime generatedAt, LocalDateTime generationCompletedAt, Long generationDurationMs, LocalDateTime createdAt) {
        this.id = id; this.platform = platform; this.localPath = localPath; this.localUrl = localUrl; this.fileName = fileName;
        this.fileSize = fileSize; this.width = width; this.height = height; this.assetType = assetType; this.mimeType = mimeType; this.status = status; this.trashOriginalStatus = trashOriginalStatus; this.prompt = prompt;
        this.negativePrompt = negativePrompt; this.mainModel = mainModel; this.lorasJson = lorasJson; this.seed = seed; this.steps = steps; this.cfg = cfg;
        this.sampler = sampler; this.scheduler = scheduler; this.workflowId = workflowId;
        this.generatedAt = generatedAt; this.generationCompletedAt = generationCompletedAt;
        this.generationDurationMs = generationDurationMs; this.createdAt = createdAt;
    }
    public long getId() { return id; }
    public String getPlatform() { return platform; }
    public String getLocalPath() { return localPath; }
    public String getLocalUrl() { return localUrl; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getAssetType() { return assetType; }
    public String getMimeType() { return mimeType; }
    public String getStatus() { return status; }
    public String getTrashOriginalStatus() { return trashOriginalStatus; }
    public String getPrompt() { return prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public String getMainModel() { return mainModel; }
    public String getLorasJson() { return lorasJson; }
    public Long getSeed() { return seed; }
    public Integer getSteps() { return steps; }
    public Double getCfg() { return cfg; }
    public String getSampler() { return sampler; }
    public String getScheduler() { return scheduler; }
    public String getWorkflowId() { return workflowId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getGenerationCompletedAt() { return generationCompletedAt; }
    public Long getGenerationDurationMs() { return generationDurationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
