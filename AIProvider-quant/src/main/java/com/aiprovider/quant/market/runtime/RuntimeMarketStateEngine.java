package com.aiprovider.quant.market.runtime;

import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;
import com.aiprovider.quant.market.stream.model.StreamMarkPriceEvent;

import java.util.List;

public interface RuntimeMarketStateEngine {
    RuntimeMarketState initialize(RuntimeMarketKey key, int maxClosedCandles,
                                  List<HistoricalCandle> seedCandles);

    RuntimeMarketUpdateResult onKline(RuntimeMarketState state, StreamKlineEvent event);

    RuntimeMarketUpdateResult onBookTicker(RuntimeMarketState state, StreamBookTickerEvent event);

    RuntimeMarketUpdateResult onMarkPrice(RuntimeMarketState state, StreamMarkPriceEvent event);
}
