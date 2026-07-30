package com.aiprovider.quant.engine.paper;

import com.aiprovider.quant.account.paper.PaperAccountSnapshot;
import com.aiprovider.quant.execution.simulation.SimulatedTopOfBook;
import com.aiprovider.quant.market.history.model.HistoricalCandle;

import java.time.Instant;
import java.util.List;

public interface PaperTradingEngine {
    default PaperTradingSessionSnapshot restore(PaperTradingSessionRestoreRequest request) {
        throw new PaperTradingException(PaperTradingException.PAPER_TRADING_RESTORE_INVALID,
                "restore is not implemented by this engine");
    }

    PaperTradingSessionSnapshot createSession(
            PaperTradingSessionConfig config, PaperAccountSnapshot paperAccount);

    PaperTradingStepResult evaluateClosedCandles(
            PaperTradingSessionSnapshot session,
            List<HistoricalCandle> candles,
            Instant evaluatedAt);

    PaperTradingStepResult executePendingOrder(
            PaperTradingSessionSnapshot session, SimulatedTopOfBook topOfBook);
}
