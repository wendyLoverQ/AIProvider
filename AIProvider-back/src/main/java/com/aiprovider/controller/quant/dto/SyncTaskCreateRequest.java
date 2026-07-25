package com.aiprovider.controller.quant.dto;

import javax.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * 历史行情同步任务创建请求（统一入口 POST /api/quant/market-data/sync-tasks）。
 *
 * 前端提交合约符号、K 线周期、起止时间（ISO 8601）和数据来源模式，
 * 后端校验真实合约并按 sourceMode 路由到对应的导入管线。
 *
 * <p>sourceMode 取值：</p>
 * <ul>
 *   <li>{@code AUTO} — 自动选择月包、日包和 REST 尾部，单任务完成全范围回填</li>
 *   <li>{@code REST_GAP_REPAIR} — 只用 /fapi/v1/klines 修补指定范围</li>
 *   <li>{@code ARCHIVE_MONTHLY} — 只导入完整月包</li>
 *   <li>{@code ARCHIVE_DAILY} — 只导入指定日包</li>
 * </ul>
 */
public class SyncTaskCreateRequest {

    /** 合约符号，如 BTCUSDT。 */
    @NotBlank
    private String symbol;

    /** K 线周期代码，如 1m、5m、15m、1h、4h、1d。 */
    @NotBlank
    private String interval;

    /** 请求起始时间（ISO 8601）。 */
    private Instant startTime;

    /** 请求结束时间（ISO 8601）。 */
    private Instant endTime;

    /** 行情提供方，当前固定 BINANCE_USDM。 */
    @NotBlank
    private String provider;

    /** 市场类型，当前固定 USDM_PERPETUAL。 */
    @NotBlank
    private String marketType;

    /** 数据来源模式，决定使用哪条导入管线。 */
    @NotBlank
    private String sourceMode;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getMarketType() { return marketType; }
    public void setMarketType(String marketType) { this.marketType = marketType; }

    public String getSourceMode() { return sourceMode; }
    public void setSourceMode(String sourceMode) { this.sourceMode = sourceMode; }
}
