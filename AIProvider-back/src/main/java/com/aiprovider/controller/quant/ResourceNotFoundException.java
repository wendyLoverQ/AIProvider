package com.aiprovider.controller.quant;

/**
 * 请求的资源不存在时抛出，由 {@link com.aiprovider.controller.ApiExceptionHandler} 映射为 HTTP 404。
 *
 * 用于任务、数据集、缺口和 K 线等资源在数据库中不存在时的明确语义，
 * 替代旧的 IllegalArgumentException（400）。
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String identifier;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " 不存在: " + identifier);
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public String getResourceType() { return resourceType; }

    public String getIdentifier() { return identifier; }
}
