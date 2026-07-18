package com.aiprovider.model.vo;

import java.time.OffsetDateTime;
import java.util.List;

public class FoundryStatusVO {
    private final boolean rpcConfigured;
    private final String rpcHost;
    private final boolean readOnly;
    private final List<Tool> tools;
    private final OffsetDateTime checkedAt;

    public FoundryStatusVO(boolean rpcConfigured, String rpcHost, boolean readOnly, List<Tool> tools, OffsetDateTime checkedAt) {
        this.rpcConfigured = rpcConfigured;
        this.rpcHost = rpcHost;
        this.readOnly = readOnly;
        this.tools = tools;
        this.checkedAt = checkedAt;
    }

    public boolean isRpcConfigured() { return rpcConfigured; }
    public String getRpcHost() { return rpcHost; }
    public boolean isReadOnly() { return readOnly; }
    public List<Tool> getTools() { return tools; }
    public OffsetDateTime getCheckedAt() { return checkedAt; }

    public static class Tool {
        private final String name;
        private final boolean available;
        private final String version;

        public Tool(String name, boolean available, String version) {
            this.name = name;
            this.available = available;
            this.version = version;
        }

        public String getName() { return name; }
        public boolean isAvailable() { return available; }
        public String getVersion() { return version; }
    }
}
