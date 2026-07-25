package com.aiprovider.quant.market.stream.port;

import com.aiprovider.quant.market.model.KlineInterval;
import com.aiprovider.quant.market.model.MarketProviderId;

/**
 * 实时行情流客户端接口。
 *
 * 负责连接上游交易所 WebSocket 并将增量事件回调给 {@link MarketStreamListener}。
 * 实现必须保证同一个订阅键（provider + symbol + interval）只维护一个上游连接，
 * 多个 listener 共享广播，最后一个订阅者离开后关闭上游连接。
 *
 * 不依赖 Spring WebSocket，不依赖 AIProvider-back。
 */
public interface MarketStreamClient {

    /**
     * 订阅指定合约和周期的实时行情流。
     *
     * @param provider  行情提供方
     * @param symbol    合约符号（大写英数字）
     * @param interval  K 线周期
     * @param listener  事件监听器
     */
    void subscribe(MarketProviderId provider, String symbol, KlineInterval interval, MarketStreamListener listener);

    /**
     * 取消订阅。如果是该订阅键的最后一个订阅者，关闭上游连接。
     *
     * @param provider  行情提供方
     * @param symbol    合约符号
     * @param interval  K 线周期
     * @param listener  要移除的监听器
     */
    void unsubscribe(MarketProviderId provider, String symbol, KlineInterval interval, MarketStreamListener listener);
}
