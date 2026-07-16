package com.aiprovider.model.vo;

public class PromptOptionVO {
    private final String id; private final String category; private final String name;
    private final String prompt; private final String type; private final String reverseId;
    private final String positivePrompt; private final String negativePrompt;
    private final int sortOrder; private final boolean enabled; private final boolean allowMultiple;
    public PromptOptionVO(String id, String category, String name, String prompt, String type, String reverseId,
                          String positivePrompt, String negativePrompt, int sortOrder, boolean enabled, boolean allowMultiple) {
        this.id = id; this.category = category; this.name = name; this.prompt = prompt; this.type = type; this.reverseId = reverseId;
        this.positivePrompt = positivePrompt; this.negativePrompt = negativePrompt; this.sortOrder = sortOrder; this.enabled = enabled; this.allowMultiple = allowMultiple;
    }
    public String getId() { return id; } public String getCategory() { return category; }
    public String getName() { return name; } public String getPrompt() { return prompt; } public String getType() { return type; } public String getReverseId() { return reverseId; }
    public String getPositivePrompt() { return positivePrompt; }
    public String getNegativePrompt() { return negativePrompt; } public int getSortOrder() { return sortOrder; }
    public boolean isEnabled() { return enabled; }
    public boolean isAllowMultiple() { return allowMultiple; }
}
