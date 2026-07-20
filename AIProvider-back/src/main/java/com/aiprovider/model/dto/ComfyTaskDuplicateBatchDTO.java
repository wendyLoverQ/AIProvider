package com.aiprovider.model.dto;

import java.util.ArrayList;
import java.util.List;

public class ComfyTaskDuplicateBatchDTO {
    private String workflowId;
    private List<String> inputSha256List = new ArrayList<>();

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public List<String> getInputSha256List() { return inputSha256List; }
    public void setInputSha256List(List<String> inputSha256List) { this.inputSha256List = inputSha256List; }
}
