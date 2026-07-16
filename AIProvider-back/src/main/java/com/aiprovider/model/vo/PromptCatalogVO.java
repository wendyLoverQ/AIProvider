package com.aiprovider.model.vo;

import java.util.List;

public class PromptCatalogVO {
    private final List<PromptOptionVO> options;
    private final List<PromptOptionVO> negativeOptions;
    private final String generalNegativePrompt;
    public PromptCatalogVO(List<PromptOptionVO> options, String generalNegativePrompt) {
        this(options, List.of(), generalNegativePrompt);
    }
    public PromptCatalogVO(List<PromptOptionVO> options, List<PromptOptionVO> negativeOptions, String generalNegativePrompt) {
        this.options = options; this.negativeOptions = negativeOptions; this.generalNegativePrompt = generalNegativePrompt;
    }
    public List<PromptOptionVO> getOptions() { return options; }
    public List<PromptOptionVO> getNegativeOptions() { return negativeOptions; }
    public String getGeneralNegativePrompt() { return generalNegativePrompt; }
}
