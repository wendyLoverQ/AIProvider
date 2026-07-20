package com.aiprovider.model.dto;

public class FavoriteMediaBatchItemDTO {
    private Long assetId;
    private String title, prompt, sourcePlatform;
    private Integer width, height;
    public Long getAssetId() { return assetId; } public void setAssetId(Long value) { assetId = value; }
    public String getTitle() { return title; } public void setTitle(String value) { title = value; }
    public String getPrompt() { return prompt; } public void setPrompt(String value) { prompt = value; }
    public String getSourcePlatform() { return sourcePlatform; } public void setSourcePlatform(String value) { sourcePlatform = value; }
    public Integer getWidth() { return width; } public void setWidth(Integer value) { width = value; }
    public Integer getHeight() { return height; } public void setHeight(Integer value) { height = value; }
}
