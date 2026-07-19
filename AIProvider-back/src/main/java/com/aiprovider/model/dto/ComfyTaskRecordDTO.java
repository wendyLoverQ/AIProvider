package com.aiprovider.model.dto;

public class ComfyTaskRecordDTO {
    private String promptId, workflowId, workflowName, promptSchemeName, positivePrompt, negativePrompt, mainModel;
    private String parametersJson, inputFile, inputSha256, inputFileName, status, resultPathsJson, errorMessage;
    public String getPromptId(){return promptId;} public void setPromptId(String v){promptId=v;}
    public String getWorkflowId(){return workflowId;} public void setWorkflowId(String v){workflowId=v;}
    public String getWorkflowName(){return workflowName;} public void setWorkflowName(String v){workflowName=v;}
    public String getPromptSchemeName(){return promptSchemeName;} public void setPromptSchemeName(String v){promptSchemeName=v;}
    public String getPositivePrompt(){return positivePrompt;} public void setPositivePrompt(String v){positivePrompt=v;}
    public String getNegativePrompt(){return negativePrompt;} public void setNegativePrompt(String v){negativePrompt=v;}
    public String getMainModel(){return mainModel;} public void setMainModel(String v){mainModel=v;}
    public String getParametersJson(){return parametersJson;} public void setParametersJson(String v){parametersJson=v;}
    public String getInputFile(){return inputFile;} public void setInputFile(String v){inputFile=v;}
    public String getInputSha256(){return inputSha256;} public void setInputSha256(String v){inputSha256=v;}
    public String getInputFileName(){return inputFileName;} public void setInputFileName(String v){inputFileName=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getResultPathsJson(){return resultPathsJson;} public void setResultPathsJson(String v){resultPathsJson=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
}
