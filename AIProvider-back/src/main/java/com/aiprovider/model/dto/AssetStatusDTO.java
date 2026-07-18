package com.aiprovider.model.dto;

import java.util.ArrayList;
import java.util.List;

public class AssetStatusDTO {
    private String platform;
    private List<Long> ids = new ArrayList<>();
    private String status;
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}