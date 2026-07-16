package com.aiprovider.model.dto;

public class PromptOptionDTO {
    private String id;
    private String category;
    private String name;
    private String prompt;
    private String type;
    private String reverseId;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean allowMultiple;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReverseId() { return reverseId; }
    public void setReverseId(String reverseId) { this.reverseId = reverseId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getAllowMultiple() { return allowMultiple; }
    public void setAllowMultiple(Boolean allowMultiple) { this.allowMultiple = allowMultiple; }
}
