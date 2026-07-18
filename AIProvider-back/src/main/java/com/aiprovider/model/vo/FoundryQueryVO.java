package com.aiprovider.model.vo;

import java.time.OffsetDateTime;

public class FoundryQueryVO {
    private final String operation;
    private final String result;
    private final OffsetDateTime executedAt;

    public FoundryQueryVO(String operation, String result, OffsetDateTime executedAt) {
        this.operation = operation;
        this.result = result;
        this.executedAt = executedAt;
    }

    public String getOperation() { return operation; }
    public String getResult() { return result; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
}
