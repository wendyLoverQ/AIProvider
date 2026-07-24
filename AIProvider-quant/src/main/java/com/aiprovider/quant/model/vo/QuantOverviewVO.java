package com.aiprovider.quant.model.vo;

import java.util.List;

/**
 * Quant 模块总览状态。
 *
 * 当前阶段固定为 {@code FOUNDATION}：基础骨架已建立，实盘交易未启用，
 * 交易所未配置，数据存储未创建。各子模块状态为 {@code SKELETON}。
 *
 * 禁止返回任何虚假的"已就绪""已连接"或伪造的业务数据。
 */
public class QuantOverviewVO {

    /** 当前阶段，固定 FOUNDATION。 */
    private String phase;
    /** 实盘交易是否启用，当前固定 false。 */
    private boolean liveTradingEnabled;
    /** 交易所配置状态，当前固定 NOT_CONFIGURED。 */
    private String exchangeState;
    /** 数据存储状态，当前固定 NOT_CREATED。 */
    private String storageState;
    /** 各基础模块及其骨架状态。 */
    private List<QuantModuleVO> modules;

    public QuantOverviewVO() {}

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public boolean isLiveTradingEnabled() { return liveTradingEnabled; }
    public void setLiveTradingEnabled(boolean liveTradingEnabled) { this.liveTradingEnabled = liveTradingEnabled; }

    public String getExchangeState() { return exchangeState; }
    public void setExchangeState(String exchangeState) { this.exchangeState = exchangeState; }

    public String getStorageState() { return storageState; }
    public void setStorageState(String storageState) { this.storageState = storageState; }

    public List<QuantModuleVO> getModules() { return modules; }
    public void setModules(List<QuantModuleVO> modules) { this.modules = modules; }
}
