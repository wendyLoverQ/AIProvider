package com.aiprovider.quant.market.stream.model;

/**
 * 实时行情流连接状态。
 *
 * <ul>
 *   <li>{@link #CONNECTING} — 正在连接 Binance WebSocket</li>
 *   <li>{@link #LIVE} — 实时连接已建立，正在接收增量数据</li>
 *   <li>{@link #RECONNECTING} — 连接断开后正在重连</li>
 *   <li>{@link #DISCONNECTED} — 已断开（正常关闭或上游关闭）</li>
 *   <li>{@link #FAILED} — 连接失败，不再重试</li>
 * </ul>
 */
public enum StreamStatus {
    CONNECTING,
    LIVE,
    RECONNECTING,
    DISCONNECTED,
    FAILED
}
