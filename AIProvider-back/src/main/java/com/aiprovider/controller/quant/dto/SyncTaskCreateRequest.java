package com.aiprovider.controller.quant.dto;

import java.time.Instant;

/**
 * 历史行情同步任务创建请求。
 *
 * 前端提交合约符号、K 线周期和起止时间（ISO 8601），
 * 后端校验后创建任务并提交到同步执行器。
 */
public class SyncTaskCreateRequest {

    /** 合约符号，如 BTCUSDT。 */
    private String symbol;

    /** K 线周期代码，如 1m、5m、15m、1h、4h、1d。 */
    private String interval;

    /** 请求起始时间（ISO 8601）。 */
    private Instant startTime;

    /** 请求结束时间（ISO 8601）。 */
    private Instant endTime;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
}
