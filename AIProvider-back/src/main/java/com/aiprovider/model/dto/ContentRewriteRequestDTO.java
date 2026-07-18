package com.aiprovider.model.dto;

public class ContentRewriteRequestDTO {
    private String sourceText; private String sourceAuthor; private String sourceUrl; private String extraInstruction;
    public String getSourceText(){return sourceText;} public void setSourceText(String v){sourceText=v;}
    public String getSourceAuthor(){return sourceAuthor;} public void setSourceAuthor(String v){sourceAuthor=v;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
    public String getExtraInstruction(){return extraInstruction;} public void setExtraInstruction(String v){extraInstruction=v;}
}
