package com.aiprovider.model.vo;

import org.springframework.core.io.Resource;

public class FileTransferPreview {
    private final String fileName;
    private final long fileSize;
    private final String mediaType;
    private final Resource resource;

    public FileTransferPreview(String fileName, long fileSize, String mediaType, Resource resource) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mediaType = mediaType;
        this.resource = resource;
    }

    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getMediaType() { return mediaType; }
    public Resource getResource() { return resource; }
}
