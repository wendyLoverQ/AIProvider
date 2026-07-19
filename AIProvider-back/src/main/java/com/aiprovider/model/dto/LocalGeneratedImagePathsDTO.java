package com.aiprovider.model.dto;

import java.util.ArrayList;
import java.util.List;

public class LocalGeneratedImagePathsDTO {
    private String platform;
    private List<String> paths = new ArrayList<>();
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public List<String> getPaths() { return paths; }
    public void setPaths(List<String> paths) { this.paths = paths; }
}
