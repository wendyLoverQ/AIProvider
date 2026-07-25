package com.aiprovider.quant.market.stream.port;

import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;
import com.aiprovider.quant.market.stream.model.StreamStatusEvent;
import com.aiprovider.quant.market.stream.model.StreamTickerEvent;

/**
 * 实时行情流事件监听器。
 *
 * 由 {@link com.aiprovider.quant.market.stream.port.MarketStreamClient} 在收到上游事件时回调。
 * 实现必须线程安全，因为 Binance WebSocket 回调可能在不同线程触发。
 */
public interface MarketStreamListener {

    void onStatus(StreamStatusEvent event);

    void onKline(StreamKlineEvent event);

    void onTicker(StreamTickerEvent event);

    void onMarkPrice(StreamMarkPriceEvent event);

    void onBookTicker(StreamBookTickerEvent event);
}
