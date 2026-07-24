package com.aiprovider.quant.model.vo;

/**
 * Quant 单个模块的骨架状态描述。
 *
 * 只描述当前真实骨架状态，禁止返回"在线""正常运行""已连接""已就绪"等虚假状态，
 * 禁止伪造余额、盈亏、订单、仓位、策略数量或回测结果。
 */
public class QuantModuleVO {

    private String key;
    private String name;
    private String description;
    private String group;
    private String status;

    public QuantModuleVO() {}

    public QuantModuleVO(String key, String name, String description, String group, String status) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.group = group;
        this.status = status;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
