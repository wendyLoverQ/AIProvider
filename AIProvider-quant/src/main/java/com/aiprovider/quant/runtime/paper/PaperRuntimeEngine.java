package com.aiprovider.quant.runtime.paper;

import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.market.history.model.HistoricalCandle;
import com.aiprovider.quant.market.stream.model.StreamBookTickerEvent;
import com.aiprovider.quant.market.stream.model.StreamKlineEvent;

import java.util.List;

public interface PaperRuntimeEngine {
    PaperRuntimeSnapshot initialize(PaperRuntimeConfig config,
                                    List<HistoricalCandle> seedCandles,
                                    PaperAccountSnapshot account);

    PaperRuntimeStepResult onKline(PaperRuntimeSnapshot runtime, StreamKlineEvent event);

    PaperRuntimeStepResult onBookTicker(PaperRuntimeSnapshot runtime, StreamBookTickerEvent event);
}
