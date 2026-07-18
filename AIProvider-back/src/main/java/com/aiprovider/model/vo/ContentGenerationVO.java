package com.aiprovider.model.vo;

import java.time.LocalDateTime;

public class ContentGenerationVO {
    private final Long generationId; private final String generationType; private final String provider; private final String model;
    private final String text; private final long latencyMs; private final LocalDateTime generatedAt;
    public ContentGenerationVO(Long generationId,String generationType,String provider,String model,String text,long latencyMs,LocalDateTime generatedAt){
        this.generationId=generationId;this.generationType=generationType;this.provider=provider;this.model=model;this.text=text;this.latencyMs=latencyMs;this.generatedAt=generatedAt;
    }
    public Long getGenerationId(){return generationId;} public String getGenerationType(){return generationType;} public String getProvider(){return provider;}
    public String getModel(){return model;} public String getText(){return text;} public long getLatencyMs(){return latencyMs;} public LocalDateTime getGeneratedAt(){return generatedAt;}
}
