package com.aiprovider.model.vo;

public class PromptTranslationVO {
    private final String positivePrompt;
    private final String negativePrompt;

    public PromptTranslationVO(String positivePrompt, String negativePrompt) {
        this.positivePrompt = positivePrompt;
        this.negativePrompt = negativePrompt;
    }

    public String getPositivePrompt() { return positivePrompt; }
    public String getNegativePrompt() { return negativePrompt; }
}
