package com.aiprovider.model.vo;

public class AssetPromptVO {
    private final String prompt;
    private final String negativePrompt;
    private final long weight;

    public AssetPromptVO(String prompt, String negativePrompt, long weight) {
        this.prompt = prompt;
        this.negativePrompt = negativePrompt;
        this.weight = weight;
    }
    public String getPrompt() { return prompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public long getWeight() { return weight; }
}
