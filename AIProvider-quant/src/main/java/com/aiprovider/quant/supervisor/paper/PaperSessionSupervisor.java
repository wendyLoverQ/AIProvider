package com.aiprovider.quant.supervisor.paper;

import com.aiprovider.quant.market.stream.port.MarketStreamListener;

import java.time.Instant;

public interface PaperSessionSupervisor extends MarketStreamListener {
    PaperSessionSupervisorSnapshot start(Instant startedAt);

    PaperSessionSupervisorSnapshot stop(Instant stoppedAt);

    PaperSessionSupervisorSnapshot getSnapshot();
}
