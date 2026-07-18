package com.aiprovider.model.dto;

public class PromptTranslationDTO {
    private String positivePrompt;
    private String negativePrompt;

    public String getPositivePrompt() { return positivePrompt; }
    public void setPositivePrompt(String positivePrompt) { this.positivePrompt = positivePrompt; }
    public String getNegativePrompt() { return negativePrompt; }
    public void setNegativePrompt(String negativePrompt) { this.negativePrompt = negativePrompt; }
}
