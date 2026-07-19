package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class FavoriteMediaVO {
    private final long id;
    private final Long assetId;
    private final String originalFileName;
    private final String title;
    private final String mediaType;
    private final String contentType;
    private final long fileSize;
    private final Integer width;
    private final Integer height;
    private final String prompt;
    private final String sourcePlatform;
    private final LocalDateTime createdAt;
    private final String contentUrl;

    public FavoriteMediaVO(long id, Long assetId, String originalFileName, String title, String mediaType,
                           String contentType, long fileSize, Integer width, Integer height, String prompt,
                           String sourcePlatform, LocalDateTime createdAt) {
        this.id = id; this.assetId = assetId; this.originalFileName = originalFileName; this.title = title;
        this.mediaType = mediaType; this.contentType = contentType; this.fileSize = fileSize;
        this.width = width; this.height = height; this.prompt = prompt; this.sourcePlatform = sourcePlatform;
        this.createdAt = createdAt; this.contentUrl = "/api/favorites/" + id + "/content";
    }
    public long getId() { return id; }
    public Long getAssetId() { return assetId; }
    public String getOriginalFileName() { return originalFileName; }
    public String getTitle() { return title; }
    public String getMediaType() { return mediaType; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public String getPrompt() { return prompt; }
    public String getSourcePlatform() { return sourcePlatform; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getContentUrl() { return contentUrl; }
}
