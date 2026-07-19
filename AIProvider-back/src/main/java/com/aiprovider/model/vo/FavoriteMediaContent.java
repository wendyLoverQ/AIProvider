package com.aiprovider.model.vo;

import org.springframework.core.io.Resource;

public class FavoriteMediaContent {
    private final String fileName;
    private final String contentType;
    private final long fileSize;
    private final Resource resource;
    public FavoriteMediaContent(String fileName, String contentType, long fileSize, Resource resource) {
        this.fileName = fileName; this.contentType = contentType; this.fileSize = fileSize; this.resource = resource;
    }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Resource getResource() { return resource; }
}
